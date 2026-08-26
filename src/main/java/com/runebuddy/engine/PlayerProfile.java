package com.runebuddy.engine;

import com.runebuddy.data.Skills;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

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
	 * Real level in a skill, defaulting to whatever that skill starts at.
	 */
	public int level(Skill skill)
	{
		Integer level = levels.get(skill);
		return level == null ? Skills.startingLevel(skill) : level;
	}

	/**
	 * True if the player has at least one of the item anywhere we can see.
	 */
	public boolean owns(int itemId)
	{
		return ownedItems.getOrDefault(itemId, 0) > 0;
	}

	public boolean hasCompleted(Quest quest)
	{
		return completedQuests.contains(quest);
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
