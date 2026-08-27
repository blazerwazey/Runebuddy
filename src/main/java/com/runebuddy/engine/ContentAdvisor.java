package com.runebuddy.engine;

import com.runebuddy.data.ContentActivity;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import com.runebuddy.data.GearExpectation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Works out what a player can actually go and do.
 *
 * <p>Levels alone are a poor answer to that. A maxed account still wearing rune meets the
 * stated requirements for a great deal it should not attempt, so where an activity says
 * what gear it expects, that is checked against the player's real equipment bonuses as
 * well.
 */
public class ContentAdvisor
{
	/**
	 * How far short an activity can be and still be worth showing as nearly in reach.
	 */
	private static final int CLOSE_HORIZON = 12;

	private final DataStore data;

	public ContentAdvisor(DataStore data)
	{
		this.data = data;
	}

	/**
	 * Sorts every activity in the given categories into ready, close, and locked.
	 *
	 * @param categories which kinds of activity to include
	 */
	public ContentAdvice advise(PlayerProfile profile, Set<ContentCategory> categories,
								@Nullable RequirementReport.ItemNameResolver itemNames)
	{
		List<ContentSuggestion> ready = new ArrayList<>();
		List<ContentSuggestion> close = new ArrayList<>();
		List<ContentSuggestion> locked = new ArrayList<>();

		for (ContentActivity activity : data.getContent())
		{
			if (!categories.contains(activity.getCategory()) || !isPossible(activity, profile))
			{
				continue;
			}

			ContentSuggestion suggestion = evaluate(activity, profile, itemNames);

			if (suggestion.isReady())
			{
				ready.add(suggestion);
			}
			else if (suggestion.getDistance() <= CLOSE_HORIZON)
			{
				close.add(suggestion);
			}
			else
			{
				locked.add(suggestion);
			}
		}

		// Ready things keep their data-file order, which is roughly easiest first. The
		// other two sort by how close they are, since that is the useful ordering when
		// you are deciding what to work toward.
		close.sort(Comparator.comparingInt(ContentSuggestion::getDistance));
		locked.sort(Comparator.comparingInt(ContentSuggestion::getDistance));

		return new ContentAdvice(
			Collections.unmodifiableList(ready),
			Collections.unmodifiableList(close),
			Collections.unmodifiableList(locked));
	}

	/**
	 * Whether the account can ever do this, regardless of level.
	 */
	private boolean isPossible(ContentActivity activity, PlayerProfile profile)
	{
		if (activity.isMembers() && !profile.isMembers())
		{
			return false;
		}

		return activity.isIronmanFriendly() || !profile.isIronman();
	}

	private ContentSuggestion evaluate(ContentActivity activity, PlayerProfile profile,
									   @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		RequirementReport report = RequirementReport.check(activity.getRequirements(), profile, itemNames);

		if (profile.isIronman() && activity.hasIronmanGate())
		{
			report = RequirementReport.combine(report,
				RequirementReport.check(activity.getIronmanRequirements(), profile, itemNames));
		}

		GearCheck gear = checkGear(activity.getRecommendedGear(), profile);

		int distance = report.getDistance() + gear.penalty;

		return new ContentSuggestion(activity, report, report.isSatisfied(),
			gear.ready, gear.advice, distance);
	}

	/**
	 * Compares what the activity expects against the player's best offensive bonus in
	 * that style.
	 *
	 * <p>Silent when the activity has no expectation, and silent when we have not seen
	 * the player's gear: telling someone they are under-geared because their bank has
	 * never been opened would be worse than saying nothing.
	 */
	private GearCheck checkGear(@Nullable GearExpectation expectation, PlayerProfile profile)
	{
		if (expectation == null || expectation.getStyle() == null || expectation.getBonus() <= 0)
		{
			return GearCheck.fine();
		}

		if (profile.getItemStats().isEmpty())
		{
			return GearCheck.fine();
		}

		GearCategory style = expectation.getStyle();
		int best = bestOffensiveBonus(profile, style);

		if (best >= expectation.getBonus())
		{
			return GearCheck.fine();
		}

		String advice = "Your " + style.getLabel().toLowerCase() + " gear is light for this"
			+ " (about " + best + " against " + expectation.getBonus() + " worth having)";

		// Being under-geared is a softer block than missing a level, and it is the sort
		// of thing a trip to the Grand Exchange fixes, so it counts for less.
		return new GearCheck(false, advice, 4);
	}

	/**
	 * The player's total offensive bonus for a style, taking the best owned item in each
	 * slot. Uses the live equipment data, so it covers gear no data file mentions.
	 */
	private int bestOffensiveBonus(PlayerProfile profile, GearCategory style)
	{
		Map<EquipSlot, Integer> bySlot = new java.util.EnumMap<>(EquipSlot.class);

		for (Map.Entry<Integer, EquipmentStats> entry : profile.getItemStats().entrySet())
		{
			EquipmentStats stats = entry.getValue();
			EquipSlot slot = stats.slot();
			if (slot == null)
			{
				continue;
			}

			int bonus = offensiveBonus(stats, style);
			bySlot.merge(slot, bonus, Math::max);
		}

		int total = 0;
		for (int bonus : bySlot.values())
		{
			// Only count slots that help; a negative piece elsewhere is not evidence the
			// player is under-geared for this.
			total += Math.max(0, bonus);
		}

		return total;
	}

	private int offensiveBonus(EquipmentStats stats, GearCategory style)
	{
		switch (style)
		{
			case RANGED:
				return stats.getRangedAttack();
			case MAGIC:
				return stats.getMagicAttack();
			case MELEE:
			default:
				return Math.max(stats.getStabAttack(),
					Math.max(stats.getSlashAttack(), stats.getCrushAttack()));
		}
	}

	/**
	 * The outcome of a gear check: whether it passed, what to say, and what it costs in
	 * the readiness ordering.
	 */
	private static final class GearCheck
	{
		private final boolean ready;
		private final String advice;
		private final int penalty;

		private GearCheck(boolean ready, @Nullable String advice, int penalty)
		{
			this.ready = ready;
			this.advice = advice;
			this.penalty = penalty;
		}

		private static GearCheck fine()
		{
			return new GearCheck(true, null, 0);
		}
	}
}
