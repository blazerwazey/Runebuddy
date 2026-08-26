package com.runebuddy.engine;

import lombok.Value;

/**
 * One requirement, rendered for a specific account: the text to show and whether the
 * player meets it.
 */
@Value
public class RequirementStatus
{
	/**
	 * What the player needs, phrased for display: "70 Attack", "Monkey Madness I".
	 */
	String label;

	boolean met;

	/**
	 * How far off the player is, used to rank the "unlocks soon" list. Levels short for
	 * a skill requirement, or a flat cost for a quest or item. Zero when met.
	 */
	int distance;

	/**
	 * Requirements we cannot verify, such as diary tiers, are shown but never block.
	 */
	boolean advisory;

	static RequirementStatus met(String label)
	{
		return new RequirementStatus(label, true, 0, false);
	}

	static RequirementStatus unmet(String label, int distance)
	{
		return new RequirementStatus(label, false, distance, false);
	}

	static RequirementStatus advisory(String label)
	{
		return new RequirementStatus(label, true, 0, true);
	}
}
