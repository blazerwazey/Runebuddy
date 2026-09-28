package com.runebuddy.engine;

import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Where the player stands on the combat achievement reward tiers.
 *
 * <p>Both the points and the thresholds come from the game, so a change to how many
 * points a tier needs is picked up without touching the plugin.
 */
@Value
public class CombatAchievementProgress
{
	/**
	 * Within this fraction of the next tier, it counts as within reach.
	 */
	private static final double CLOSE_FRACTION = 0.2;

	/**
	 * Or within this many points, whichever is more generous.
	 */
	private static final int CLOSE_POINTS = 10;

	int points;

	/**
	 * The highest tier reached, or null before the first.
	 */
	@Nullable
	CombatAchievementTier reached;

	/**
	 * The tier being worked toward, or null once every tier is done.
	 */
	@Nullable
	CombatAchievementTier next;

	/**
	 * Points the next tier needs in total, or 0 when there is none.
	 */
	int nextThreshold;

	/**
	 * Points the reached tier needed, or 0 before the first.
	 */
	int reachedThreshold;

	/**
	 * Works out progress, or returns null when the thresholds have not arrived from the
	 * server yet: guessing them would put wrong numbers on screen.
	 */
	@Nullable
	public static CombatAchievementProgress of(int points, Map<CombatAchievementTier, Integer> thresholds)
	{
		CombatAchievementTier reached = null;
		CombatAchievementTier next = null;
		int reachedThreshold = 0;
		int nextThreshold = 0;

		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			Integer threshold = thresholds.get(tier);
			if (threshold == null || threshold <= 0)
			{
				return null;
			}

			if (points >= threshold)
			{
				reached = tier;
				reachedThreshold = threshold;
			}
			else if (next == null)
			{
				next = tier;
				nextThreshold = threshold;
			}
		}

		return new CombatAchievementProgress(points, reached, next, nextThreshold, reachedThreshold);
	}

	public int pointsToNext()
	{
		return next == null ? 0 : nextThreshold - points;
	}

	/**
	 * Fraction of the way from the reached tier to the next, in 0..1.
	 */
	public double fractionToNext()
	{
		if (next == null)
		{
			return 1d;
		}

		int span = Math.max(1, nextThreshold - reachedThreshold);
		return Math.max(0d, Math.min(1d, (double) (points - reachedThreshold) / span));
	}

	/**
	 * True when the next tier is close enough to be worth a nudge.
	 */
	public boolean isNextTierClose()
	{
		if (next == null)
		{
			return false;
		}

		int span = nextThreshold - reachedThreshold;
		return pointsToNext() <= Math.max(CLOSE_POINTS, span * CLOSE_FRACTION);
	}

	/**
	 * "142 points, 38 to Hard".
	 */
	public String summary()
	{
		if (next == null)
		{
			return points + " points, every tier done";
		}

		return points + " points, " + pointsToNext() + " to " + next.getLabel();
	}
}
