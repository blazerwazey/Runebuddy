package com.runebuddy.engine;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One thing standing between the player and a goal.
 */
@Value
public class GoalStep
{
	public enum Kind
	{
		/**
		 * Train a skill. The only kind with an honest time estimate.
		 */
		SKILL,
		QUEST,
		ITEM,
		/**
		 * Something we can describe but not check, such as a diary tier.
		 */
		NOTE
	}

	Kind kind;

	/**
	 * What to do, phrased for display: "Train Slayer to 85", "Complete Dragon Slayer II".
	 */
	String label;

	/**
	 * Estimated hours, or null when the step cannot honestly be timed.
	 */
	@Nullable
	Double hours;

	/**
	 * The skill to train, for a skill step.
	 */
	@Nullable
	Skill skill;

	/**
	 * The recommended way to train it, for a skill step.
	 */
	@Nullable
	Route route;
}
