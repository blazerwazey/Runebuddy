package com.runebuddy.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * One way of training one skill, as described by the bundled data files.
 *
 * <p>Instances are created by Gson and then frozen by {@link #resolve}; nothing mutates
 * them afterwards, so they are safe to read from both the client thread and the EDT.
 */
@Getter
public class TrainingMethod
{
	/**
	 * Stable identifier, unique across the data file. Used in tests and log messages.
	 */
	private String id;

	private String skill;
	private String name;

	/**
	 * Lowest level at which this is worth doing at all.
	 */
	private int minLevel;

	/**
	 * Level past which better options exist. The method is still listed above this,
	 * ranked down, because "still fine, just not optimal" is useful information.
	 */
	private int recommendedUntil;

	private List<XpBreakpoint> xpCurve;

	/**
	 * Gold per hour: negative is a cost, positive is a profit.
	 */
	private int gpPerHour;

	private String effort;
	private boolean members;

	/**
	 * False for methods that only make sense when you can buy the inputs.
	 */
	private boolean ironmanFriendly = true;

	private Requirements requirements;

	@Nullable
	private String location;

	@Nullable
	private String notes;

	@Nullable
	private String wikiUrl;

	private transient Skill resolvedSkill;
	private transient Effort resolvedEffort;

	/**
	 * The skill this trains.
	 */
	public Skill getSkill()
	{
		return resolvedSkill;
	}

	/**
	 * How much attention this demands.
	 */
	public Effort getEffort()
	{
		return resolvedEffort;
	}

	public Requirements getRequirements()
	{
		return requirements == null ? Requirements.none() : requirements;
	}

	/**
	 * Experience per hour at the given level, linearly interpolated between the
	 * curve's breakpoints and clamped at both ends.
	 */
	public int xpAt(int level)
	{
		List<XpBreakpoint> curve = xpCurve;
		XpBreakpoint first = curve.get(0);
		if (level <= first.getLevel())
		{
			return first.getXpPerHour();
		}

		for (int i = 1; i < curve.size(); i++)
		{
			XpBreakpoint hi = curve.get(i);
			if (level <= hi.getLevel())
			{
				XpBreakpoint lo = curve.get(i - 1);
				int span = hi.getLevel() - lo.getLevel();
				if (span <= 0)
				{
					return hi.getXpPerHour();
				}

				double t = (double) (level - lo.getLevel()) / span;
				return (int) Math.round(lo.getXpPerHour() + t * (hi.getXpPerHour() - lo.getXpPerHour()));
			}
		}

		return curve.get(curve.size() - 1).getXpPerHour();
	}

	/**
	 * True once the player has out-levelled this method's useful range.
	 */
	public boolean isOutgrown(int level)
	{
		return level > recommendedUntil;
	}

	/**
	 * Validates the raw fields and resolves them to API enums.
	 *
	 * @param warn sink for non-fatal data problems
	 * @throws IllegalArgumentException if the entry is unusable
	 */
	void resolve(Consumer<String> warn)
	{
		if (id == null || id.trim().isEmpty())
		{
			throw new IllegalArgumentException("training method is missing an id");
		}

		if (name == null || name.trim().isEmpty())
		{
			throw new IllegalArgumentException(id + ": missing name");
		}

		try
		{
			resolvedSkill = Skill.valueOf(String.valueOf(skill).toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(id + ": unknown skill '" + skill + "'");
		}

		if (Skills.isAggregate(resolvedSkill))
		{
			throw new IllegalArgumentException(id + ": " + resolvedSkill + " is not a trainable skill");
		}

		try
		{
			resolvedEffort = Effort.valueOf(String.valueOf(effort).toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(id + ": unknown effort '" + effort + "'");
		}

		if (minLevel < 1 || minLevel > 99)
		{
			throw new IllegalArgumentException(id + ": minLevel must be 1-99");
		}

		if (recommendedUntil <= 0)
		{
			recommendedUntil = 99;
		}

		if (recommendedUntil < minLevel)
		{
			throw new IllegalArgumentException(id + ": recommendedUntil is below minLevel");
		}

		if (xpCurve == null || xpCurve.isEmpty())
		{
			throw new IllegalArgumentException(id + ": xpCurve must have at least one point");
		}

		List<XpBreakpoint> sorted = new ArrayList<>(xpCurve);
		sorted.sort((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));
		int previous = Integer.MIN_VALUE;
		for (XpBreakpoint point : sorted)
		{
			if (point.getLevel() == previous)
			{
				throw new IllegalArgumentException(id + ": duplicate xpCurve level " + point.getLevel());
			}

			if (point.getXpPerHour() < 0)
			{
				throw new IllegalArgumentException(id + ": negative xpPerHour at level " + point.getLevel());
			}

			previous = point.getLevel();
		}
		xpCurve = Collections.unmodifiableList(sorted);

		if (requirements == null)
		{
			requirements = Requirements.none();
		}
		else
		{
			requirements.resolve(id, warn);
		}

		Integer required = getRequirements().getSkillLevels().get(resolvedSkill);
		if (required != null && required > minLevel)
		{
			warn.accept(id + ": requires " + required + " " + Skills.displayName(resolvedSkill)
				+ " but minLevel is " + minLevel);
		}
	}
}
