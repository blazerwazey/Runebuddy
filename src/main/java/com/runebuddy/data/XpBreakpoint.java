package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One point on a method's experience curve: "at this level, this method gives
 * roughly this much experience per hour".
 *
 * <p>Fields are non-final and there is a no-arg constructor because Gson populates
 * these by reflection; nothing mutates them after {@link DataStore} has loaded.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class XpBreakpoint
{
	private int level;
	private int xpPerHour;
}
