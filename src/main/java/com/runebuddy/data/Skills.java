package com.runebuddy.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/**
 * Helpers for working with the {@link Skill} enum, which carries a {@code OVERALL}
 * aggregate entry that is not a skill you can train. Filtering it here means its
 * deprecation is suppressed in exactly one place.
 */
public final class Skills
{
	@SuppressWarnings("deprecation")
	private static final Skill AGGREGATE = Skill.OVERALL;

	private static final List<Skill> TRAINABLE;

	static
	{
		List<Skill> trainable = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (skill != AGGREGATE)
			{
				trainable.add(skill);
			}
		}

		TRAINABLE = Collections.unmodifiableList(trainable);
	}

	private Skills()
	{
	}

	/**
	 * Every skill a player can actually train, in the game's own order.
	 */
	public static List<Skill> trainable()
	{
		return TRAINABLE;
	}

	/**
	 * True for the {@code OVERALL} pseudo-skill.
	 */
	public static boolean isAggregate(Skill skill)
	{
		return skill == AGGREGATE;
	}

	/**
	 * "Woodcutting" rather than "WOODCUTTING".
	 */
	public static String displayName(Skill skill)
	{
		String name = skill.name().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
