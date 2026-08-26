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
	 * Whether the item can be bought. Untradeable items have no price to weigh against
	 * what a player can afford, so they are judged on their requirements alone.
	 */
	private boolean tradeable = true;

	/**
	 * How you get hold of one: "Grand Exchange", "Abyssal demons", "Fight Caves". The
	 * only answer that matters on an ironman, and still useful to a main for the
	 * untradeables.
	 */
	@Nullable
	private String source;

	/**
	 * For {@link GearCategory#SKILLING}: the skill this tool is used for.
	 */
	@Nullable
	private String toolFor;

	private Requirements requirements;

	/**
	 * Extra requirements that apply only to accounts that have to obtain the item
	 * themselves. An abyssal whip asks a main for 70 Attack and some coins; it asks an
	 * ironman for 70 Attack and 85 Slayer.
	 */
	@Nullable
	private Requirements ironmanRequirements;

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
	 * The extra requirements an account that cannot buy this has to meet. Empty for
	 * items that are no harder to obtain yourself than to buy.
	 */
	public Requirements getIronmanRequirements()
	{
		return ironmanRequirements == null ? Requirements.none() : ironmanRequirements;
	}

	/**
	 * True when this item asks more of an account that has to obtain it itself.
	 */
	public boolean hasIronmanGate()
	{
		return !getIronmanRequirements().isEmpty();
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

		if (ironmanRequirements != null)
		{
			ironmanRequirements.resolve(name + " (ironman)", warn);
		}

		if (source == null || source.trim().isEmpty())
		{
			throw new IllegalArgumentException(name + ": missing source");
		}
	}
}
