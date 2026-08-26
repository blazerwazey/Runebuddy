package com.runebuddy.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * What an account needs before a training method or piece of gear is available to it.
 *
 * <p>The JSON carries plain strings ({@code "MINING"}, {@code "DORICS_QUEST"}); those are
 * resolved to API enums once by {@link #resolve} when the data files load, so the engine
 * never re-parses. Skill names that do not resolve are a data bug and fail the load.
 * Quest names that do not resolve are downgraded to a note instead, because the data
 * files are expected to outlive any particular version of the RuneLite API and a quest
 * we cannot name is not worth hiding a method over.
 */
public class Requirements
{
	private static final Requirements NONE = resolved(new Requirements());

	// Raw, as deserialised. Any of these may be null when the JSON omits them.
	private Map<String, Integer> skills;
	private List<String> quests;
	private List<Integer> items;
	private int questPoints;
	private List<String> notes;

	/**
	 * Minimum real level per skill.
	 */
	@Getter
	private transient Map<Skill, Integer> skillLevels = Collections.emptyMap();

	/**
	 * Quests that must be complete.
	 */
	@Getter
	private transient Set<Quest> requiredQuests = Collections.emptySet();

	/**
	 * Quest names from the data file that this client version does not know about.
	 * Surfaced to the user as advisory text rather than enforced.
	 */
	@Getter
	private transient List<String> unknownQuests = Collections.emptyList();

	/**
	 * Item ids that the method effectively needs (a pickaxe, a specific weapon).
	 */
	@Getter
	private transient List<Integer> requiredItems = Collections.emptyList();

	/**
	 * Free-text requirements we cannot check, such as diary tiers or minigame access.
	 */
	@Getter
	private transient List<String> advisoryNotes = Collections.emptyList();

	/**
	 * An empty requirement set, already resolved.
	 */
	public static Requirements none()
	{
		return NONE;
	}

	/**
	 * Builds an already-resolved requirement set. Intended for tests; production
	 * instances come from Gson and are resolved by {@link DataStore}.
	 */
	public static Requirements of(Map<Skill, Integer> skillLevels, Set<Quest> quests, List<Integer> items)
	{
		Requirements r = new Requirements();
		r.skillLevels = skillLevels == null ? Collections.emptyMap() : new LinkedHashMap<>(skillLevels);
		r.requiredQuests = quests == null ? Collections.emptySet() : new LinkedHashSet<>(quests);
		r.requiredItems = items == null ? Collections.emptyList() : List.copyOf(items);
		return r;
	}

	private static Requirements resolved(Requirements r)
	{
		r.resolve("", w -> {
		});
		return r;
	}

	public int getQuestPoints()
	{
		return questPoints;
	}

	/**
	 * True when there is nothing at all to check.
	 */
	public boolean isEmpty()
	{
		return skillLevels.isEmpty()
			&& requiredQuests.isEmpty()
			&& requiredItems.isEmpty()
			&& questPoints == 0;
	}

	/**
	 * Turns the raw strings into API enums.
	 *
	 * @param ownerId id of the method or gear item, used in warnings
	 * @param warn    sink for non-fatal data problems
	 * @throws IllegalArgumentException if a skill name does not resolve
	 */
	void resolve(String ownerId, Consumer<String> warn)
	{
		Map<Skill, Integer> levels = new LinkedHashMap<>();
		if (skills != null)
		{
			for (Map.Entry<String, Integer> e : skills.entrySet())
			{
				Skill skill;
				try
				{
					skill = Skill.valueOf(e.getKey().toUpperCase());
				}
				catch (IllegalArgumentException ex)
				{
					throw new IllegalArgumentException(ownerId + ": unknown skill '" + e.getKey() + "'");
				}

				if (e.getValue() == null || e.getValue() < 1 || e.getValue() > 99)
				{
					throw new IllegalArgumentException(ownerId + ": level for " + skill + " must be 1-99");
				}

				levels.put(skill, e.getValue());
			}
		}
		skillLevels = Collections.unmodifiableMap(levels);

		Set<Quest> resolvedQuests = new LinkedHashSet<>();
		List<String> unresolved = new ArrayList<>();
		if (quests != null)
		{
			for (String name : quests)
			{
				try
				{
					resolvedQuests.add(Quest.valueOf(name.toUpperCase()));
				}
				catch (IllegalArgumentException ex)
				{
					warn.accept(ownerId + ": unknown quest '" + name + "', shown as a note instead");
					unresolved.add(prettifyQuestName(name));
				}
			}
		}
		requiredQuests = Collections.unmodifiableSet(resolvedQuests);
		unknownQuests = Collections.unmodifiableList(unresolved);

		requiredItems = items == null ? Collections.emptyList() : List.copyOf(items);
		advisoryNotes = notes == null ? Collections.emptyList() : List.copyOf(notes);

		if (questPoints < 0)
		{
			throw new IllegalArgumentException(ownerId + ": questPoints cannot be negative");
		}
	}

	/**
	 * {@code MONKEY_MADNESS_I} to {@code Monkey madness i} — good enough for a note.
	 */
	private static String prettifyQuestName(String enumName)
	{
		String spaced = enumName.toLowerCase().replace('_', ' ').trim();
		return spaced.isEmpty() ? enumName : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
