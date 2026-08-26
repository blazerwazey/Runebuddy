package com.runebuddy.engine;

import java.util.List;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * Everything the panel shows for one skill.
 */
@Value
public class SkillAdvice
{
	Skill skill;

	int level;

	/**
	 * Methods the player can do right now, best first.
	 */
	List<MethodScore> recommended;

	/**
	 * Methods just out of reach, closest first. Empty when the user has turned the
	 * upcoming-unlocks list off.
	 */
	List<MethodScore> unlockingSoon;

	/**
	 * True when the data file has nothing for this skill at all.
	 */
	public boolean isEmpty()
	{
		return recommended.isEmpty() && unlockingSoon.isEmpty();
	}
}
