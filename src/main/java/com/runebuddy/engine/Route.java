package com.runebuddy.engine;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * A plan for getting one skill from where it is to a target level: which method to use
 * over which levels, and roughly how long and how much gold that takes.
 */
@Value
public class Route
{
	Skill skill;

	RouteStyle style;

	int fromLevel;

	int targetLevel;

	List<RouteLeg> legs;

	/**
	 * Hours of training across every leg. When the route is blocked this covers only
	 * the part that can be planned.
	 */
	double hours;

	/**
	 * Gold across every leg: negative is a cost, positive is a profit.
	 */
	long gold;

	/**
	 * The level at which no known method is available, or null when the route reaches
	 * its target.
	 */
	@Nullable
	Integer blockedAtLevel;

	public boolean isComplete()
	{
		return blockedAtLevel == null;
	}

	/**
	 * True when the player is already at or past the target.
	 */
	public boolean isAlreadyThere()
	{
		return fromLevel >= targetLevel;
	}
}
