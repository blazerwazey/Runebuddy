package com.runebuddy.engine;

import com.runebuddy.data.TrainingMethod;
import lombok.Value;

/**
 * A training method as it applies to one account: its rate at the player's level, the
 * requirement check, and where it landed in the ranking.
 *
 * <p>The raw {@link #score} is never shown to the user — {@link #rationale} is the
 * human-readable version of the same judgement.
 */
@Value
public class MethodScore
{
	TrainingMethod method;

	/**
	 * Weighted score in 0..1. Comparable only against other methods scored in the same
	 * pass, since the experience term is normalised against that candidate set.
	 */
	double score;

	/**
	 * Experience per hour at the player's current level.
	 */
	int xpPerHour;

	/**
	 * Gold per hour: negative is a cost, positive is a profit.
	 */
	int gpPerHour;

	RequirementReport requirements;

	/**
	 * True when the player has passed the method's useful level range.
	 */
	boolean outgrown;

	/**
	 * One short line explaining why this ranked where it did.
	 */
	String rationale;
}
