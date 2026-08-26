package com.runebuddy.engine;

import com.runebuddy.data.Requirements;
import com.runebuddy.data.Skills;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * The result of checking a {@link Requirements} against a {@link PlayerProfile}.
 *
 * <p>Nothing is thrown away: requirements the player fails are kept with their labels so
 * the panel can show <em>why</em> something is locked, and how close they are.
 */
public class RequirementReport
{
	/**
	 * Cost assigned to a missing quest when ranking how close an unlock is. Roughly
	 * "worth a few levels of effort" — enough that a quest-locked method sorts below
	 * one that only needs a level or two.
	 */
	private static final int QUEST_DISTANCE = 8;

	/**
	 * Cost assigned to a missing item. Lower than a quest because most are buyable.
	 */
	private static final int ITEM_DISTANCE = 3;

	@Getter
	private final List<RequirementStatus> statuses;

	@Getter
	private final boolean satisfied;

	/**
	 * Combined distance across every unmet requirement. Zero when satisfied.
	 */
	@Getter
	private final int distance;

	private RequirementReport(List<RequirementStatus> statuses)
	{
		this.statuses = Collections.unmodifiableList(statuses);

		boolean allMet = true;
		int total = 0;
		for (RequirementStatus status : statuses)
		{
			if (!status.isMet())
			{
				allMet = false;
				total += status.getDistance();
			}
		}

		this.satisfied = allMet;
		this.distance = total;
	}

	/**
	 * Checks every requirement against the profile.
	 *
	 * @param itemNames resolves an item id to a display name, or null to fall back to
	 *                  the raw id
	 */
	public static RequirementReport check(Requirements requirements, PlayerProfile profile,
										  @Nullable ItemNameResolver itemNames)
	{
		List<RequirementStatus> statuses = new ArrayList<>();

		for (Map.Entry<Skill, Integer> entry : requirements.getSkillLevels().entrySet())
		{
			Skill skill = entry.getKey();
			int needed = entry.getValue();
			int have = profile.level(skill);
			String label = needed + " " + Skills.displayName(skill);

			statuses.add(have >= needed
				? RequirementStatus.met(label)
				: RequirementStatus.unmet(label, needed - have));
		}

		for (Quest quest : requirements.getRequiredQuests())
		{
			statuses.add(profile.hasCompleted(quest)
				? RequirementStatus.met(quest.getName())
				: RequirementStatus.unmet(quest.getName(), QUEST_DISTANCE));
		}

		if (requirements.getQuestPoints() > 0)
		{
			int needed = requirements.getQuestPoints();
			String label = needed + " quest points";
			statuses.add(profile.getQuestPoints() >= needed
				? RequirementStatus.met(label)
				: RequirementStatus.unmet(label, QUEST_DISTANCE));
		}

		for (int itemId : requirements.getRequiredItems())
		{
			String name = itemNames == null ? null : itemNames.nameOf(itemId);
			String label = name == null ? "Item " + itemId : name;

			// Without a bank snapshot we cannot claim the player lacks something, so an
			// unseen bank makes item requirements advisory rather than blocking.
			if (profile.owns(itemId))
			{
				statuses.add(RequirementStatus.met(label));
			}
			else if (profile.isBankKnown())
			{
				statuses.add(RequirementStatus.unmet(label, ITEM_DISTANCE));
			}
			else
			{
				statuses.add(RequirementStatus.advisory(label));
			}
		}

		for (String note : requirements.getUnknownQuests())
		{
			statuses.add(RequirementStatus.advisory(note));
		}

		for (String note : requirements.getAdvisoryNotes())
		{
			statuses.add(RequirementStatus.advisory(note));
		}

		return new RequirementReport(statuses);
	}

	/**
	 * Merges two reports into one covering both sets of requirements.
	 *
	 * <p>Used to layer the extra demands an ironman faces on top of the ones everybody
	 * faces. Satisfied and distance are derived from the merged status list, so a
	 * failure in either half carries through.
	 */
	public static RequirementReport combine(RequirementReport first, RequirementReport second)
	{
		List<RequirementStatus> merged = new ArrayList<>(first.statuses);
		merged.addAll(second.statuses);
		return new RequirementReport(merged);
	}

	/**
	 * The requirements the player does not meet, in the order they were checked.
	 */
	public List<RequirementStatus> unmet()
	{
		List<RequirementStatus> unmet = new ArrayList<>();
		for (RequirementStatus status : statuses)
		{
			if (!status.isMet())
			{
				unmet.add(status);
			}
		}

		return unmet;
	}

	/**
	 * A short phrase naming what is blocking, for the locked-item line in the panel.
	 */
	public String blockingSummary()
	{
		List<RequirementStatus> unmet = unmet();
		if (unmet.isEmpty())
		{
			return "";
		}

		if (unmet.size() == 1)
		{
			return "needs " + unmet.get(0).getLabel();
		}

		return "needs " + unmet.get(0).getLabel() + " and " + (unmet.size() - 1) + " more";
	}

	/**
	 * Resolves item ids to display names. The plugin backs this with the client's item
	 * manager; tests pass a stub or null.
	 */
	public interface ItemNameResolver
	{
		@Nullable
		String nameOf(int itemId);
	}
}
