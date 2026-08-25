package com.runebuddy.data;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * One rung on a gear ladder. Ladders are keyed by ({@link GearCategory},
 * {@link EquipSlot}) and ordered by {@link #getTier()}, so "what should I aim for"
 * is a walk up the ladder until the requirements stop being met.
 */
@Getter
public class GearItem
{
	/**
	 * Item id, used for the icon, the price lookup, and matching against what the
	 * player owns.
	 */
	private int itemId;

	private String name;
	private String slot;
	private String category;

	/**
	 * Position on the ladder. Higher is better. Unique within a slot and category.
	 */
	private int tier;

	private boolean members;

	/**
	 * For {@link GearCategory#SKILLING}: the skill this tool is used for.
	 */
	@Nullable
	private String toolFor;

	private Requirements requirements;

	@Nullable
	private String notes;

	private transient EquipSlot resolvedSlot;
	private transient GearCategory resolvedCategory;
	private transient Skill resolvedToolFor;

	public EquipSlot getSlot()
	{
		return resolvedSlot;
	}

	public GearCategory getCategory()
	{
		return resolvedCategory;
	}

	/**
	 * The skill this tool serves, or null if it is not a skilling tool.
	 */
	@Nullable
	public Skill getToolFor()
	{
		return resolvedToolFor;
	}

	public Requirements getRequirements()
	{
		return requirements == null ? Requirements.none() : requirements;
	}

	/**
	 * Validates the raw fields and resolves them to API enums.
	 *
	 * @param warn sink for non-fatal data problems
	 * @throws IllegalArgumentException if the entry is unusable
	 */
	void resolve(Consumer<String> warn)
	{
		if (name == null || name.trim().isEmpty())
		{
			throw new IllegalArgumentException("gear item " + itemId + " is missing a name");
		}

		if (itemId <= 0)
		{
			throw new IllegalArgumentException(name + ": itemId must be positive");
		}

		try
		{
			resolvedSlot = EquipSlot.valueOf(String.valueOf(slot).toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(name + ": unknown slot '" + slot + "'");
		}

		try
		{
			resolvedCategory = GearCategory.valueOf(String.valueOf(category).toUpperCase());
		}
		catch (IllegalArgumentException ex)
		{
			throw new IllegalArgumentException(name + ": unknown category '" + category + "'");
		}

		if (toolFor != null)
		{
			try
			{
				resolvedToolFor = Skill.valueOf(toolFor.toUpperCase());
			}
			catch (IllegalArgumentException ex)
			{
				throw new IllegalArgumentException(name + ": unknown toolFor skill '" + toolFor + "'");
			}
		}

		if (resolvedCategory == GearCategory.SKILLING && resolvedToolFor == null)
		{
			throw new IllegalArgumentException(name + ": skilling gear must declare toolFor");
		}

		if (resolvedCategory != GearCategory.SKILLING && resolvedToolFor != null)
		{
			throw new IllegalArgumentException(name + ": toolFor is only valid on skilling gear");
		}

		if (tier < 0)
		{
			throw new IllegalArgumentException(name + ": tier cannot be negative");
		}

		if (requirements == null)
		{
			requirements = Requirements.none();
		}
		else
		{
			requirements.resolve(name, warn);
		}
	}
}
