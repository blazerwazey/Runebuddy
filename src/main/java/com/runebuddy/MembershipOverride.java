package com.runebuddy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lets the user override the members detection, which is otherwise read from the
 * world type of the world they are logged into.
 */
@AllArgsConstructor
@Getter
public enum MembershipOverride
{
	AUTO("Auto-detect"),
	FREE_TO_PLAY("Free-to-play"),
	MEMBERS("Members");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
