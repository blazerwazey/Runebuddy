package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Which progression ladder a piece of gear belongs to. Every ladder is ranked
 * independently, so a slot can have a different "best you qualify for" per style.
 */
@AllArgsConstructor
@Getter
public enum GearCategory
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	PRAYER("Prayer"),
	SKILLING("Skilling tools");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
