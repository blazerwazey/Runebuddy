package com.runebuddy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lets the user override the ironman detection, which is otherwise read from
 * {@link net.runelite.api.Client#getAccountType()}.
 */
@AllArgsConstructor
@Getter
public enum AccountTypeOverride
{
	AUTO("Auto-detect"),
	MAIN("Main"),
	IRONMAN("Ironman");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
