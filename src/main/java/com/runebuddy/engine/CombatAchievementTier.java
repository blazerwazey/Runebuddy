package com.runebuddy.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The combat achievement reward tiers, lowest first.
 */
@AllArgsConstructor
@Getter
public enum CombatAchievementTier
{
	EASY("Easy"),
	MEDIUM("Medium"),
	HARD("Hard"),
	ELITE("Elite"),
	MASTER("Master"),
	GRANDMASTER("Grandmaster");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
