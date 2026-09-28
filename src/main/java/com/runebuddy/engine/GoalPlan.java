package com.runebuddy.engine;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * A goal worked backwards into what is left to do.
 */
@Value
public class GoalPlan
{
	Goal goal;

	/**
	 * The goal, phrased for display: "99 Slayer", "Bandos chestplate", "Fire cape".
	 */
	String title;

	/**
	 * False when the goal points at something the data no longer has.
	 */
	boolean recognised;

	/**
	 * True when there is nothing left to do. For an activity that means ready to go,
	 * since whether it has been done is not something the plugin can see.
	 */
	boolean complete;

	/**
	 * What is left, in the order worth doing it. Notes we cannot check may remain after
	 * the goal is complete.
	 */
	List<GoalStep> steps;

	/**
	 * Hours across every step that can be timed.
	 */
	double hours;

	/**
	 * Steps with no estimate: quests, items, notes. Called out so the hours are never
	 * read as the whole story.
	 */
	int untimedSteps;

	/**
	 * Progress in 0..1, where it can be measured; null otherwise.
	 */
	@Nullable
	Double progress;

	/**
	 * The first thing to do, or null when complete. Notes are never the next step, since
	 * there is nothing to act on.
	 */
	@Nullable
	public GoalStep nextStep()
	{
		for (GoalStep step : steps)
		{
			if (step.getKind() != GoalStep.Kind.NOTE)
			{
				return step;
			}
		}

		return null;
	}
}
