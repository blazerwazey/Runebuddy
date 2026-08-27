package com.runebuddy.data;

import javax.annotation.Nullable;
import lombok.Getter;

/**
 * How well geared an activity expects you to be, as a style and an offensive bonus.
 *
 * <p>Levels alone are a poor guide to whether someone should attempt a boss: a maxed
 * account in rune is not ready for what its stats suggest. Checking against real
 * equipment bonuses catches that.
 */
@Getter
public class GearExpectation
{
	private String style;

	/**
	 * Offensive bonus worth having for the named style, in the units the game uses for
	 * attack bonuses.
	 */
	private int bonus;

	private transient GearCategory resolvedStyle;

	/**
	 * The style this expects, or null when the activity does not care which you use.
	 */
	@Nullable
	public GearCategory getStyle()
	{
		return resolvedStyle;
	}

	void resolve(String ownerId)
	{
		if (style == null || "ANY".equalsIgnoreCase(style))
		{
			return;
		}

		try
		{
			resolvedStyle = GearCategory.valueOf(style.toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(ownerId + ": unknown gear style '" + style + "'");
		}

		if (resolvedStyle == GearCategory.SKILLING)
		{
			throw new IllegalArgumentException(ownerId + ": gear expectation must name a combat style");
		}
	}
}
