package com.runebuddy.engine;

import com.runebuddy.data.ContentActivity;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.data.GearCategory;
import com.runebuddy.data.GearItem;
import com.runebuddy.data.Skills;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * Assembles the "what next" feed from every other advisor, in priority buckets.
 */
public class NextActions
{
	/**
	 * A requirement crossed within this many levels counts as newly unlocked. Stateless
	 * on purpose: it needs no history, and it fades on its own as the player moves on.
	 */
	private static final int NEWLY_UNLOCKED_LEVELS = 2;

	private static final int MAX_QUICK_WINS = 4;
	private static final int MAX_GEAR_WINS = 2;
	private static final int MAX_UNLOCK_WINS = 2;
	private static final int MAX_TRAINING = 4;

	private final Advisors advisors;

	NextActions(Advisors advisors)
	{
		this.advisors = advisors;
	}

	/**
	 * Everything the feed needs besides the profile.
	 */
	@Value
	@Builder
	public static class Inputs
	{
		List<Goal> goals;
		GearCategory style;
		EngineSettings settings;
		Set<ContentCategory> contentCategories;

		@Nullable
		RequirementReport.ItemNameResolver itemNames;

		@Nullable
		GearAdvisor.PriceResolver prices;
	}

	public List<NextAction> build(PlayerProfile profile, Inputs in)
	{
		List<NextAction> feed = new ArrayList<>();
		addSlayer(feed, profile);
		Set<Integer> goalItems = addGoals(feed, profile, in);
		addQuickWins(feed, profile, in, goalItems);
		addTraining(feed, profile, in);
		return feed;
	}

	private void addSlayer(List<NextAction> feed, PlayerProfile profile)
	{
		SlayerAdvice advice = advisors.getSlayer().advise(profile);
		if (advice == null)
		{
			return;
		}

		SlayerTask task = advice.getTask();
		feed.add(new NextAction(NextAction.Bucket.PINNED,
			task.getRemaining() + " " + task.getName() + " left",
			advice.getVerdict(), null, NextAction.Destination.SKILLS, Skill.SLAYER));
	}

	/**
	 * The next step of each open goal. Two goals needing the same step share one entry.
	 *
	 * @return item ids that goals already cover, so quick wins do not repeat them
	 */
	private Set<Integer> addGoals(List<NextAction> feed, PlayerProfile profile, Inputs in)
	{
		Map<String, GoalStep> steps = new LinkedHashMap<>();
		Map<String, List<String>> forGoals = new LinkedHashMap<>();
		Map<String, Goal> firstGoal = new LinkedHashMap<>();
		Set<Integer> goalItems = new HashSet<>();

		for (GoalPlan plan : advisors.getGoals().planAll(in.getGoals(), profile, in.getSettings(), in.getItemNames()))
		{
			if (plan.getGoal().getType() == Goal.Type.GEAR)
			{
				goalItems.add(plan.getGoal().getItemId());
			}

			GoalStep next = plan.nextStep();
			if (!plan.isRecognised() || plan.isComplete() || next == null)
			{
				continue;
			}

			steps.putIfAbsent(next.getLabel(), next);
			forGoals.computeIfAbsent(next.getLabel(), k -> new ArrayList<>()).add(plan.getTitle());
			firstGoal.putIfAbsent(next.getLabel(), plan.getGoal());
		}

		for (Map.Entry<String, GoalStep> entry : steps.entrySet())
		{
			GoalStep step = entry.getValue();
			List<String> titles = forGoals.get(entry.getKey());
			String detail = "Toward " + joinTitles(titles);

			NextAction.Destination destination;
			Skill skill = null;
			switch (step.getKind())
			{
				case SKILL:
					destination = NextAction.Destination.SKILLS;
					skill = step.getSkill();
					break;
				case ITEM:
					destination = firstGoal.get(entry.getKey()).getType() == Goal.Type.CONTENT
						? NextAction.Destination.DO
						: NextAction.Destination.GEAR;
					break;
				default:
					destination = NextAction.Destination.PLAN;
			}

			feed.add(new NextAction(NextAction.Bucket.GOAL, step.getLabel(), detail, step.getHours(),
				destination, skill));
		}

		return goalItems;
	}

	private void addQuickWins(List<NextAction> feed, PlayerProfile profile, Inputs in, Set<Integer> goalItems)
	{
		List<NextAction> wins = new ArrayList<>();

		CombatAchievementProgress ca = profile.combatAchievements();
		if (ca != null && ca.isNextTierClose())
		{
			wins.add(new NextAction(NextAction.Bucket.QUICK_WIN,
				ca.pointsToNext() + " points to " + ca.getNext().getLabel() + " combat achievements",
				ca.getPoints() + " points so far", null, NextAction.Destination.DO, null));
		}

		int gear = 0;
		List<GearSuggestion> suggestions = new ArrayList<>();
		// Without prices "next upgrade" ignores cost, which is fine on the Gear tab where
		// the price sits beside it, but a necklace of anguish is not a quick win. Ironmen
		// do not buy their gear, so price never applies to them.
		if (in.getPrices() != null || profile.isIronman())
		{
			suggestions.addAll(advisors.getGear().adviseCombat(in.getStyle(), profile, in.getItemNames(), in.getPrices()));
			suggestions.addAll(advisors.getGear().adviseTools(profile, in.getItemNames(), in.getPrices()));
		}
		for (GearSuggestion suggestion : suggestions)
		{
			GearItem next = suggestion.getNext();
			if (gear >= MAX_GEAR_WINS || next == null || goalItems.contains(next.getItemId()))
			{
				continue;
			}

			wins.add(new NextAction(NextAction.Bucket.QUICK_WIN, "Get " + next.getName(),
				suggestion.label() + " upgrade you can use now", null, NextAction.Destination.GEAR, null));
			gear++;
		}

		int unlocks = 0;
		ContentAdvice content = advisors.getContent().advise(profile, in.getContentCategories(), in.getItemNames());
		for (ContentSuggestion suggestion : content.getReady())
		{
			if (unlocks >= MAX_UNLOCK_WINS)
			{
				break;
			}

			String crossed = justCrossed(suggestion.getActivity(), profile);
			if (crossed != null)
			{
				wins.add(new NextAction(NextAction.Bucket.QUICK_WIN, "New: " + suggestion.getActivity().getName(),
					"Unlocked at " + crossed, null, NextAction.Destination.DO, null));
				unlocks++;
			}
		}

		feed.addAll(wins.subList(0, Math.min(MAX_QUICK_WINS, wins.size())));
	}

	/**
	 * The level requirement the player crossed most recently, if it was within the last
	 * couple of levels: "70 Ranged". Null when nothing was crossed lately.
	 */
	@Nullable
	private static String justCrossed(ContentActivity activity, PlayerProfile profile)
	{
		for (Map.Entry<Skill, Integer> entry : activity.getRequirements().getSkillLevels().entrySet())
		{
			int needed = entry.getValue();
			int margin = profile.level(entry.getKey()) - needed;
			if (needed > Skills.startingLevel(entry.getKey()) + NEWLY_UNLOCKED_LEVELS
				&& margin >= 0 && margin <= NEWLY_UNLOCKED_LEVELS)
			{
				return needed + " " + Skills.displayName(entry.getKey());
			}
		}

		return null;
	}

	private void addTraining(List<NextAction> feed, PlayerProfile profile, Inputs in)
	{
		for (MethodScore score : advisors.getEngine().topOverall(profile, in.getSettings(), in.getItemNames(), MAX_TRAINING))
		{
			Skill skill = score.getMethod().getSkill();
			int level = profile.level(skill);
			Double hours = null;
			if (level < TimeEstimator.MAX_LEVEL)
			{
				double toNext = TimeEstimator.hoursToLevel(score.getMethod(), profile.xp(skill), level + 1);
				hours = Double.isInfinite(toNext) ? null : toNext;
			}

			feed.add(new NextAction(NextAction.Bucket.TRAINING,
				Skills.displayName(skill) + ": " + score.getMethod().getName(),
				hours == null ? score.getRationale() : "Next level in " + formatHours(hours),
				null, NextAction.Destination.SKILLS, skill));
		}
	}

	private static String formatHours(double hours)
	{
		return hours < 1 ? Math.max(1, Math.round(hours * 60)) + " min" : String.format(java.util.Locale.ROOT, "%.1fh", hours);
	}

	private static String joinTitles(List<String> titles)
	{
		if (titles.size() == 1)
		{
			return titles.get(0);
		}

		return String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.get(titles.size() - 1);
	}
}
