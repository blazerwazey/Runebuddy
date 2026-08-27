package com.runebuddy.engine;

import com.runebuddy.data.Skills;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemVariationMapping;

/**
 * An immutable snapshot of everything the advice depends on.
 *
 * <p>Built on the client thread by {@link ProfileTracker} and handed to the Swing panel,
 * which must never touch the client itself. Because it is immutable, handing it across
 * threads needs no further synchronisation.
 */
@Getter
@Builder(toBuilder = true)
public class PlayerProfile
{
	/**
	 * A profile for a player who is not logged in. The panel renders a prompt instead
	 * of advice when it sees this.
	 */
	public static final PlayerProfile LOGGED_OUT = PlayerProfile.builder().loggedIn(false).build();

	private final boolean loggedIn;

	/**
	 * Real (unboosted) level per skill. Missing entries read as level 1.
	 */
	@Singular("level")
	private final Map<Skill, Integer> levels;

	/**
	 * Quests known to be finished. Only quests the data files reference are checked.
	 */
	@Singular("completedQuest")
	private final Set<Quest> completedQuests;

	private final int questPoints;

	/**
	 * True when the account is any flavour of ironman, after the config override.
	 */
	private final boolean ironman;

	/**
	 * True when the player is on a members world, after the config override.
	 */
	private final boolean members;

	/**
	 * Coins across inventory and the last-seen bank. Drives the affordability term.
	 */
	private final long liquidGp;

	/**
	 * Quantity owned per item id across worn equipment, inventory and the last-seen
	 * bank snapshot.
	 */
	@Singular("owned")
	private final Map<Integer, Integer> ownedItems;

	/**
	 * Item id currently worn per equipment slot, keyed by
	 * {@link net.runelite.api.EquipmentInventorySlot#getSlotIdx()}.
	 */
	@Singular("equipped")
	private final Map<Integer, Integer> equippedItems;

	/**
	 * True when a bank has been seen for this account, so "you do not own this" can be
	 * stated rather than guessed at.
	 */
	private final boolean bankKnown;

	/**
	 * Display name per item id, resolved on the client thread when the snapshot was
	 * taken. Looking these up lazily from the panel would mean reading item definitions
	 * off the client thread, which is not allowed.
	 */
	@Singular("itemName")
	private final Map<Integer, String> itemNames;

	/**
	 * Grand Exchange price per item id, resolved alongside the names and for the same
	 * reason: the price lookup reads an item definition too.
	 */
	@Singular("itemPrice")
	private final Map<Integer, Integer> itemPrices;

	/**
	 * Equipment stats for every wearable item the player owns, resolved from the client's
	 * own live data. This is what lets the gear tab rank an item nobody hand-listed.
	 */
	@Singular("itemStats")
	private final Map<Integer, EquipmentStats> itemStats;

	/**
	 * Real level in a skill, defaulting to whatever that skill starts at.
	 */
	public int level(Skill skill)
	{
		Integer level = levels.get(skill);
		return level == null ? Skills.startingLevel(skill) : level;
	}

	/**
	 * Collapses an item id onto the one that stands for its whole family, so a glory(6)
	 * and a glory(4) are the same amulet, and a degraded moons piece is the same armour
	 * as a pristine one.
	 *
	 * <p>Backed by RuneLite's own variation table, which is static data and needs no
	 * client thread. It deliberately does not merge genuinely different items: a rune
	 * scimitar never becomes a dragon scimitar.
	 */
	public static int canonicalItem(int itemId)
	{
		return ItemVariationMapping.map(itemId);
	}

	/**
	 * True if the player has at least one of the item anywhere we can see, in any charge
	 * or condition.
	 */
	public boolean owns(int itemId)
	{
		return ownedItems.getOrDefault(canonicalItem(itemId), 0) > 0;
	}

	/**
	 * True when the given ladder item is the one worn in that equipment slot, comparing
	 * across charges and conditions.
	 */
	public boolean isWearing(int slotIdx, int itemId)
	{
		Integer worn = equippedItems.get(slotIdx);
		return worn != null && worn == canonicalItem(itemId);
	}

	public boolean hasCompleted(Quest quest)
	{
		return completedQuests.contains(quest);
	}

	/**
	 * Display name for an item, or null if it was not resolved when this snapshot was
	 * taken.
	 */
	@Nullable
	public String nameOf(int itemId)
	{
		return itemNames.get(itemId);
	}

	/**
	 * Equipment stats for an owned item, or null if it is not wearable or was not seen.
	 */
	@Nullable
	public EquipmentStats statsOf(int itemId)
	{
		return itemStats.get(canonicalItem(itemId));
	}

	/**
	 * Grand Exchange price for an item, or 0 when unknown or untradeable.
	 */
	public int priceOf(int itemId)
	{
		return itemPrices.getOrDefault(itemId, 0);
	}

	/**
	 * Resolves item names for requirement labels, backed by this snapshot.
	 */
	public RequirementReport.ItemNameResolver itemNameResolver()
	{
		return this::nameOf;
	}

	/**
	 * Resolves prices for the gear advisor, backed by this snapshot.
	 */
	public GearAdvisor.PriceResolver priceResolver()
	{
		return this::priceOf;
	}

	/**
	 * Sum of every real level, the number shown on the in-game skills tab.
	 */
	public int totalLevel()
	{
		int total = 0;
		for (Skill skill : Skills.trainable())
		{
			total += level(skill);
		}

		return total;
	}

	/**
	 * Combat level, computed from the melee, ranged, magic and prayer levels.
	 */
	public int combatLevel()
	{
		return Experience.getCombatLevel(
			level(Skill.ATTACK),
			level(Skill.STRENGTH),
			level(Skill.DEFENCE),
			level(Skill.HITPOINTS),
			level(Skill.MAGIC),
			level(Skill.RANGED),
			level(Skill.PRAYER));
	}

	/**
	 * Convenience for tests: a members main with every skill at the given level.
	 */
	public static PlayerProfile flat(int level)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skills.trainable())
		{
			levels.put(skill, Math.max(level, Skills.startingLevel(skill)));
		}

		return PlayerProfile.builder()
			.loggedIn(true)
			.members(true)
			.levels(levels)
			.build();
	}
}
