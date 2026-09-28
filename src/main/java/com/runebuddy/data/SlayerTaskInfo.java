package com.runebuddy.data;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Advice for one Slayer task, keyed by the task name the game's own tables use.
 *
 * <p>The task the player has is always read from the game, never from this file; this
 * only adds advice on top. A task missing from the file is still shown, just without it.
 */
@Getter
public class SlayerTaskInfo
{
	/**
	 * The task name as the game gives it, matched ignoring case: "Abyssal demons".
	 */
	private String task;

	/**
	 * Where to do it, best first.
	 */
	private List<String> locations;

	/**
	 * The combat style it wants, when there is a clear one.
	 */
	private String style;

	private boolean cannonable;

	private String verdict;

	/**
	 * Why the verdict is what it is, in a few words.
	 */
	@Nullable
	private String why;

	@Nullable
	private String notes;

	@Nullable
	private String wikiUrl;

	private transient GearCategory resolvedStyle;
	private transient SlayerVerdict resolvedVerdict;

	@Nullable
	public GearCategory getStyle()
	{
		return resolvedStyle;
	}

	public SlayerVerdict getVerdict()
	{
		return resolvedVerdict;
	}

	public List<String> getLocations()
	{
		return locations == null ? Collections.emptyList() : locations;
	}

	void resolve(Consumer<String> warn)
	{
		if (task == null || task.trim().isEmpty())
		{
			throw new IllegalArgumentException("slayer task is missing its name");
		}

		if (style != null)
		{
			try
			{
				resolvedStyle = GearCategory.valueOf(style.toUpperCase());
			}
			catch (IllegalArgumentException ex)
			{
				throw new IllegalArgumentException(task + ": unknown style '" + style + "'");
			}

			if (resolvedStyle == GearCategory.PRAYER || resolvedStyle == GearCategory.SKILLING)
			{
				throw new IllegalArgumentException(task + ": style must be melee, ranged or magic");
			}
		}

		try
		{
			resolvedVerdict = verdict == null ? SlayerVerdict.DO : SlayerVerdict.valueOf(verdict.toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(task + ": unknown verdict '" + verdict + "'");
		}

		locations = locations == null ? Collections.emptyList() : List.copyOf(locations);
		if (locations.isEmpty())
		{
			warn.accept(task + ": no locations listed");
		}
	}
}
