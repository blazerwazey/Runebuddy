package com.runebuddy.engine;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * Something the player has said they want: a level, an item, or an activity.
 *
 * <p>Stored as JSON per RuneScape account, so the fields are deliberately plain. A goal
 * that points at data which no longer exists is kept rather than silently dropped, and
 * shown as unrecognised so the player can decide what to do with it.
 */
@Value
public class Goal
{
	public enum Type
	{
		SKILL,
		GEAR,
		CONTENT
	}

	Type type;

	/**
	 * The skill to train, for a skill goal.
	 */
	@Nullable
	Skill skill;

	/**
	 * The level to reach, for a skill goal.
	 */
	int level;

	/**
	 * The item to own, for a gear goal.
	 */
	int itemId;

	/**
	 * The activity to be ready for, for a content goal.
	 */
	@Nullable
	String activityId;

	public static Goal skill(Skill skill, int level)
	{
		return new Goal(Type.SKILL, skill, level, 0, null);
	}

	public static Goal gear(int itemId)
	{
		return new Goal(Type.GEAR, null, 0, itemId, null);
	}

	public static Goal content(String activityId)
	{
		return new Goal(Type.CONTENT, null, 0, 0, activityId);
	}

	/**
	 * What makes two goals the same goal. A skill has one target at a time, so setting
	 * a new level replaces the old one rather than stacking.
	 */
	public String key()
	{
		switch (type)
		{
			case SKILL:
				return "skill:" + skill;
			case GEAR:
				return "gear:" + itemId;
			case CONTENT:
				return "content:" + activityId;
			default:
				return "unknown";
		}
	}

	/**
	 * False for a goal read back from storage that is missing what its type needs.
	 */
	public boolean isWellFormed()
	{
		if (type == null)
		{
			return false;
		}

		switch (type)
		{
			case SKILL:
				return skill != null && level >= 2 && level <= TimeEstimator.MAX_LEVEL;
			case GEAR:
				return itemId > 0;
			case CONTENT:
				return activityId != null && !activityId.isEmpty();
			default:
				return false;
		}
	}
}
