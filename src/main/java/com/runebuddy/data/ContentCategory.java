package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What kind of thing an activity is, used to group the Content tab and to let a player
 * filter down to the kind they care about.
 */
@AllArgsConstructor
@Getter
public enum ContentCategory
{
	BOSS("Bosses"),
	RAID("Raids"),
	MINIGAME("Minigames"),
	SKILLING("Skilling"),
	QUEST("Quests"),
	DIARY("Diaries"),
	UNLOCK("Unlocks");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
