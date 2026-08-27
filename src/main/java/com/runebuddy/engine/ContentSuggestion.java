package com.runebuddy.engine;

import com.runebuddy.data.ContentActivity;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * An activity as it applies to one account: whether they are ready, and if not, what is
 * in the way.
 */
@Value
public class ContentSuggestion
{
	ContentActivity activity;

	RequirementReport requirements;

	/**
	 * True when the levels and quests are all met.
	 */
	boolean requirementsMet;

	/**
	 * True when the player's gear reaches what the activity expects. Always true when the
	 * activity expects nothing in particular, or when we cannot see their gear yet.
	 */
	boolean gearReady;

	/**
	 * The gear shortfall, phrased for display, or null when there is none to report.
	 */
	@Nullable
	String gearAdvice;

	/**
	 * How far off being ready, for ordering the "close" list. Zero when ready.
	 */
	int distance;

	/**
	 * Ready to go now: requirements met and gear up to it.
	 */
	public boolean isReady()
	{
		return requirementsMet && gearReady;
	}
}
