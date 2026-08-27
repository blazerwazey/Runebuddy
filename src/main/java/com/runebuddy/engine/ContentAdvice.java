package com.runebuddy.engine;

import java.util.List;
import lombok.Value;

/**
 * The Content tab's three answers: what you can do now, what is nearly in reach, and what
 * is still a way off.
 */
@Value
public class ContentAdvice
{
	/**
	 * Requirements met and gear up to it.
	 */
	List<ContentSuggestion> ready;

	/**
	 * Short a level or two, or under-geared for it.
	 */
	List<ContentSuggestion> close;

	/**
	 * Still well out of reach, kept so there is something to aim at.
	 */
	List<ContentSuggestion> locked;

	public boolean isEmpty()
	{
		return ready.isEmpty() && close.isEmpty() && locked.isEmpty();
	}
}
