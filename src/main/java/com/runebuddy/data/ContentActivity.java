package com.runebuddy.data;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Something a player can go and do, as described by the bundled data file.
 *
 * <p>Where a training method answers "how do I level this skill", an activity answers
 * "what should I go and do with the account I have". The two overlap on purpose —
 * Wintertodt is both — but they are read for different reasons.
 */
@Getter
public class ContentActivity
{
	/**
	 * Stable identifier, unique across the data file.
	 */
	private String id;

	private String name;
	private String category;

	private boolean members = true;

	/**
	 * False for activities that only make sense when you can buy your way in.
	 */
	private boolean ironmanFriendly = true;

	private Requirements requirements;

	/**
	 * Extra requirements that apply only to accounts that have to supply themselves.
	 */
	@Nullable
	private Requirements ironmanRequirements;

	/**
	 * Combat style the activity expects, and how much offensive bonus is worth having
	 * before attempting it. Checked against the player's actual gear.
	 */
	@Nullable
	private GearExpectation recommendedGear;

	/**
	 * What you get for doing it. This is the reason to go, so it is never optional.
	 */
	private String rewards;

	@Nullable
	private String notes;

	@Nullable
	private String wikiUrl;

	private transient ContentCategory resolvedCategory;

	public ContentCategory getCategory()
	{
		return resolvedCategory;
	}

	public Requirements getRequirements()
	{
		return requirements == null ? Requirements.none() : requirements;
	}

	public Requirements getIronmanRequirements()
	{
		return ironmanRequirements == null ? Requirements.none() : ironmanRequirements;
	}

	public boolean hasIronmanGate()
	{
		return !getIronmanRequirements().isEmpty();
	}

	/**
	 * Validates the raw fields and resolves them to enums.
	 *
	 * @param warn sink for non-fatal data problems
	 * @throws IllegalArgumentException if the entry is unusable
	 */
	void resolve(Consumer<String> warn)
	{
		if (id == null || id.trim().isEmpty())
		{
			throw new IllegalArgumentException("content activity is missing an id");
		}

		if (name == null || name.trim().isEmpty())
		{
			throw new IllegalArgumentException(id + ": missing name");
		}

		if (rewards == null || rewards.trim().isEmpty())
		{
			throw new IllegalArgumentException(id + ": missing rewards, which is the reason to go");
		}

		try
		{
			resolvedCategory = ContentCategory.valueOf(String.valueOf(category).toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(id + ": unknown category '" + category + "'");
		}

		if (requirements == null)
		{
			requirements = Requirements.none();
		}
		else
		{
			requirements.resolve(id, warn);
		}

		if (ironmanRequirements != null)
		{
			ironmanRequirements.resolve(id + " (ironman)", warn);
		}

		if (recommendedGear != null)
		{
			recommendedGear.resolve(id);
		}
	}
}
