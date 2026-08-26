package com.runebuddy.engine;

import com.runebuddy.data.DataStore;
import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import com.runebuddy.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;

/**
 * Walks the gear ladders to answer "what should I be wearing, and what next".
 *
 * <p>Each ladder is ordered worst to best. The player's position on it is the highest
 * rung they own; the goal is the highest rung whose requirements they meet; the next
 * upgrade is the best rung they meet, do not own, and can afford. The first rung above
 * the goal is reported as well, with the requirement blocking it, because "70 Attack
 * unlocks a whip" is the more useful answer to "what should I aim for".
 */
public class GearAdvisor
{
	private final DataStore data;

	public GearAdvisor(DataStore data)
	{
		this.data = data;
	}

	/**
	 * Suggestions for every slot in a combat ladder, in slot order.
	 */
	public List<GearSuggestion> adviseCombat(GearCategory category, PlayerProfile profile,
											 @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		return adviseCombat(category, profile, itemNames, null);
	}

	/**
	 * Suggestions for every slot in a combat ladder, in slot order.
	 *
	 * @param prices resolves the current price of an item so "buy next" stays within
	 *               what the player can pay, or null to ignore price
	 */
	public List<GearSuggestion> adviseCombat(GearCategory category, PlayerProfile profile,
											 @Nullable RequirementReport.ItemNameResolver itemNames,
											 @Nullable PriceResolver prices)
	{
		List<GearSuggestion> suggestions = new ArrayList<>();
		for (EquipSlot slot : EquipSlot.values())
		{
			List<GearItem> ladder = data.ladder(category, slot);
			if (ladder.isEmpty())
			{
				continue;
			}

			GearSuggestion suggestion = advise(slot, null, ladder, profile, itemNames, prices);
			if (!suggestion.isEmpty())
			{
				suggestions.add(suggestion);
			}
		}

		return Collections.unmodifiableList(suggestions);
	}

	/**
	 * Suggestions for the tool ladders, one per skill that uses tools.
	 */
	public List<GearSuggestion> adviseTools(PlayerProfile profile,
											@Nullable RequirementReport.ItemNameResolver itemNames)
	{
		return adviseTools(profile, itemNames, null);
	}

	/**
	 * Suggestions for the tool ladders, one per skill that uses tools.
	 *
	 * @param prices resolves the current price of an item, or null to ignore price
	 */
	public List<GearSuggestion> adviseTools(PlayerProfile profile,
											@Nullable RequirementReport.ItemNameResolver itemNames,
											@Nullable PriceResolver prices)
	{
		List<GearSuggestion> suggestions = new ArrayList<>();
		for (Skill skill : data.skillsWithTools())
		{
			List<GearItem> ladder = data.toolsFor(skill);
			if (ladder.isEmpty())
			{
				continue;
			}

			GearSuggestion suggestion = advise(EquipSlot.TOOL, skill, ladder, profile, itemNames, prices);
			if (!suggestion.isEmpty())
			{
				suggestions.add(suggestion);
			}
		}

		return Collections.unmodifiableList(suggestions);
	}

	/**
	 * Picks the style an account has actually invested in, used when the user has not
	 * pinned one. Ties go to melee, which is what most accounts train first.
	 */
	public static GearCategory detectStyle(PlayerProfile profile)
	{
		int melee = Math.max(profile.level(Skill.ATTACK), profile.level(Skill.STRENGTH));
		int ranged = profile.level(Skill.RANGED);
		int magic = profile.level(Skill.MAGIC);

		if (ranged > melee && ranged >= magic)
		{
			return GearCategory.RANGED;
		}

		if (magic > melee && magic > ranged)
		{
			return GearCategory.MAGIC;
		}

		return GearCategory.MELEE;
	}

	private GearSuggestion advise(EquipSlot slot, @Nullable Skill toolFor, List<GearItem> ladder,
								  PlayerProfile profile,
								  @Nullable RequirementReport.ItemNameResolver itemNames,
								  @Nullable PriceResolver prices)
	{
		List<GearItem> reachable = new ArrayList<>();
		for (GearItem item : ladder)
		{
			if (!item.isMembers() || profile.isMembers())
			{
				reachable.add(item);
			}
		}

		GearItem owned = null;
		GearItem goal = null;

		for (GearItem item : reachable)
		{
			if (profile.owns(item.getItemId()) && (owned == null || item.getTier() > owned.getTier()))
			{
				owned = item;
			}

			if (RequirementReport.check(item.getRequirements(), profile, itemNames).isSatisfied()
				&& (goal == null || item.getTier() > goal.getTier()))
			{
				goal = item;
			}
		}

		// What to aim for is the cheapest rung above the goal that is still out of
		// reach, not merely the first entry whose requirements happen to fail: an item
		// gated behind a quest can sit below one gated only on a level you already have.
		int goalTier = goal == null ? Integer.MIN_VALUE : goal.getTier();
		GearItem locked = null;
		RequirementReport lockedReport = null;

		for (GearItem item : reachable)
		{
			if (item.getTier() <= goalTier || (locked != null && item.getTier() >= locked.getTier()))
			{
				continue;
			}

			RequirementReport report = RequirementReport.check(item.getRequirements(), profile, itemNames);
			if (!report.isSatisfied())
			{
				locked = item;
				lockedReport = report;
			}
		}

		// What to buy next is the best thing they qualify for, do not own, and can
		// actually pay for. Both extremes are bad advice: stepping one rung at a time
		// tells a level 61 miner to buy a bronze pickaxe, while ignoring price tells a
		// player with 15m to go and buy a Bandos chestplate.
		GearItem next = null;
		if (goal != null && (owned == null || owned.getTier() < goal.getTier()))
		{
			int floor = owned == null ? Integer.MIN_VALUE : owned.getTier();
			next = goal;

			if (prices != null)
			{
				GearItem affordable = null;
				for (GearItem item : reachable)
				{
					if (item.getTier() <= floor || item.getTier() > goalTier)
					{
						continue;
					}

					if (!RequirementReport.check(item.getRequirements(), profile, itemNames).isSatisfied())
					{
						continue;
					}

					int price = prices.priceOf(item.getItemId());

					// A price of zero means untradeable or unknown, which is not the
					// same as free; those are judged on requirements alone.
					boolean withinBudget = price <= 0 || price <= profile.getLiquidGp();
					if (withinBudget && (affordable == null || item.getTier() > affordable.getTier()))
					{
						affordable = item;
					}
				}

				if (affordable != null)
				{
					next = affordable;
				}
			}
		}

		return new GearSuggestion(slot, toolFor, equippedIn(slot, reachable, profile),
			owned, next, goal, locked, lockedReport);
	}

	/**
	 * The ladder entry the player is wearing in this slot, if any. Tools have no
	 * dedicated slot, so a wielded tool is matched through the weapon slot.
	 */
	@Nullable
	private GearItem equippedIn(EquipSlot slot, List<GearItem> ladder, PlayerProfile profile)
	{
		EquipmentInventorySlot clientSlot = slot.getClientSlot();
		int slotIdx = clientSlot != null
			? clientSlot.getSlotIdx()
			: EquipmentInventorySlot.WEAPON.getSlotIdx();

		Integer wornId = profile.getEquippedItems().get(slotIdx);
		if (wornId == null)
		{
			return null;
		}

		for (GearItem item : ladder)
		{
			if (item.getItemId() == wornId)
			{
				return item;
			}
		}

		return null;
	}

	/**
	 * Resolves an item id to its current price. The panel backs this with the client's
	 * item manager; tests pass a stub or null.
	 */
	public interface PriceResolver
	{
		/**
		 * @return the price, or 0 when the item is untradeable or the price is unknown
		 */
		int priceOf(int itemId);
	}
}
