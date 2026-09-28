package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The common wisdom on a Slayer task.
 */
@AllArgsConstructor
@Getter
public enum SlayerVerdict
{
	DO("Worth doing"),
	SKIP("Usually skipped"),
	BLOCK("Usually blocked");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
