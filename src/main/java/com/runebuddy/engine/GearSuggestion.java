package com.runebuddy.engine;

import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearItem;
import com.runebuddy.data.Skills;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * The state of one equipment slot for one account: what is worn, what is owned, what to
 * buy next, and what to aim for.
 */
@Value
public class GearSuggestion
{
	EquipSlot slot;

	/**
	 * For a tool ladder, the skill it serves — every tool shares {@link EquipSlot#TOOL},
	 * so this is what tells a pickaxe row from an axe row. Null for combat slots.
	 */
	@Nullable
	Skill toolFor;

	/**
	 * What the player currently has equipped in this slot, if it is on the ladder.
	 */
	@Nullable
	GearItem equipped;

	/**
	 * The best item on the ladder the player owns anywhere, worn or banked.
	 */
	@Nullable
	GearItem owned;

	/**
	 * What to buy now: the best item the player qualifies for and does not own. Null
	 * when they already have it.
	 */
	@Nullable
	GearItem next;

	/**
	 * The best item the player currently qualifies for.
	 */
	@Nullable
	GearItem goal;

	/**
	 * The rung above {@link #goal}, with the requirement that blocks it. Null when the
	 * ladder has nothing further.
	 */
	@Nullable
	GearItem locked;

	/**
	 * Why {@link #locked} is out of reach, phrased for display.
	 */
	@Nullable
	RequirementReport lockedReport;

	/**
	 * What to call this row in the panel: the skill for a tool ladder, otherwise the
	 * slot name.
	 */
	public String label()
	{
		return toolFor == null ? slot.getLabel() : Skills.displayName(toolFor);
	}

	/**
	 * True when the player is already using the best thing they qualify for.
	 */
	public boolean isSatisfied()
	{
		return next == null && goal != null && owned != null && owned.getTier() >= goal.getTier();
	}

	/**
	 * True when there is nothing to say about this slot at all.
	 */
	public boolean isEmpty()
	{
		return equipped == null && owned == null && next == null && goal == null && locked == null;
	}
}
