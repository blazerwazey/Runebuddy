package com.runebuddy.engine;

import com.runebuddy.data.ContentActivity;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.GearItem;
import com.runebuddy.data.Requirements;
import com.runebuddy.data.Skills;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Works a goal backwards into steps: each missing level becomes a training route with an
 * estimate; missing quests and items become steps without one, because an honest figure
 * for those does not exist.
 */
public class GoalPlanner
{
	/**
	 * Skills whose training feeds Hitpoints.
	 */
	private static final Set<Skill> COMBAT_SKILLS = java.util.EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);

	private final DataStore data;
	private final TimeEstimator estimator;
	private final ContentAdvisor contentAdvisor;

	public GoalPlanner(DataStore data, TimeEstimator estimator, ContentAdvisor contentAdvisor)
	{
		this.data = data;
		this.estimator = estimator;
		this.contentAdvisor = contentAdvisor;
	}

	public GoalPlan plan(Goal goal, PlayerProfile profile, EngineSettings settings,
						 @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		switch (goal.getType())
		{
			case SKILL:
				return planSkill(goal, profile, settings, itemNames);
			case GEAR:
				return planGear(goal, profile, settings, itemNames);
			case CONTENT:
				return planContent(goal, profile, settings, itemNames);
			default:
				return unrecognised(goal, "Unknown goal");
		}
	}

	/**
	 * Plans every goal, in the order given.
	 */
	public List<GoalPlan> planAll(List<Goal> goals, PlayerProfile profile, EngineSettings settings,
								  @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		List<GoalPlan> plans = new ArrayList<>();
		for (Goal goal : goals)
		{
			plans.add(plan(goal, profile, settings, itemNames));
		}

		return plans;
	}

	private GoalPlan planSkill(Goal goal, PlayerProfile profile, EngineSettings settings,
							   @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		Skill skill = goal.getSkill();
		String title = goal.getLevel() + " " + Skills.displayName(skill);

		List<GoalStep> steps = new ArrayList<>();
		GoalStep step = trainStep(skill, goal.getLevel(), profile, settings, itemNames);
		if (step != null)
		{
			steps.add(step);
		}

		int targetXp = Experience.getXpForLevel(goal.getLevel());
		double progress = Math.min(1d, (double) profile.xp(skill) / targetXp);

		return finish(goal, title, steps, progress);
	}

	private GoalPlan planGear(Goal goal, PlayerProfile profile, EngineSettings settings,
							  @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		GearItem item = data.gearItem(goal.getItemId());
		if (item == null)
		{
			String name = itemNames == null ? null : itemNames.nameOf(goal.getItemId());
			return unrecognised(goal, name != null ? name : "Item " + goal.getItemId());
		}

		if (profile.owns(item.getItemId()))
		{
			return finish(goal, item.getName(), Collections.emptyList(), 1d);
		}

		Requirements ironman = profile.isIronman() && item.hasIronmanGate() ? item.getIronmanRequirements() : null;
		List<GoalStep> steps = requirementSteps(item.getRequirements(), ironman, profile, settings, itemNames);

		steps.add(new GoalStep(GoalStep.Kind.ITEM, "Get it: " + item.getSource(), null, null, null));

		return finish(goal, item.getName(), steps, null);
	}

	private GoalPlan planContent(Goal goal, PlayerProfile profile, EngineSettings settings,
								 @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		ContentActivity activity = data.activity(goal.getActivityId());
		if (activity == null)
		{
			return unrecognised(goal, "Activity no longer listed");
		}

		Requirements ironman = profile.isIronman() && activity.hasIronmanGate()
			? activity.getIronmanRequirements()
			: null;
		List<GoalStep> steps = requirementSteps(activity.getRequirements(), ironman, profile, settings, itemNames);

		ContentSuggestion suggestion = contentAdvisor.evaluate(activity, profile, itemNames);
		if (!suggestion.isGearReady() && suggestion.getGearAdvice() != null)
		{
			// Gear is checkable, so it is a real step rather than a note: it blocks
			// "ready" in the Do tab too.
			steps.add(new GoalStep(GoalStep.Kind.ITEM, suggestion.getGearAdvice(), null, null, null));
		}

		return finish(goal, activity.getName(), steps, null);
	}

	/**
	 * Turns a requirement set (and any ironman extras) into steps, skipping whatever the
	 * player already meets.
	 */
	private List<GoalStep> requirementSteps(Requirements requirements, @Nullable Requirements ironman,
											PlayerProfile profile, EngineSettings settings,
											@Nullable RequirementReport.ItemNameResolver itemNames)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Set<Quest> quests = new LinkedHashSet<>();
		Set<Integer> items = new LinkedHashSet<>();
		Set<String> notes = new LinkedHashSet<>();
		int questPoints = 0;

		for (Requirements set : ironman == null
			? Collections.singletonList(requirements)
			: java.util.Arrays.asList(requirements, ironman))
		{
			set.getSkillLevels().forEach((skill, level) -> levels.merge(skill, level, Math::max));
			quests.addAll(set.getRequiredQuests());
			items.addAll(set.getRequiredItems());
			notes.addAll(set.getUnknownQuests());
			notes.addAll(set.getAdvisoryNotes());
			questPoints = Math.max(questPoints, set.getQuestPoints());
		}

		List<GoalStep> skillSteps = new ArrayList<>();
		long combatXp = 0;
		for (Map.Entry<Skill, Integer> entry : levels.entrySet())
		{
			if (entry.getKey() == Skill.HITPOINTS)
			{
				continue;
			}

			GoalStep step = trainStep(entry.getKey(), entry.getValue(), profile, settings, itemNames);
			if (step != null)
			{
				skillSteps.add(step);
				if (COMBAT_SKILLS.contains(entry.getKey()))
				{
					combatXp += Math.max(0, Experience.getXpForLevel(entry.getValue()) - profile.xp(entry.getKey()));
				}
			}
		}

		Integer hitpoints = levels.get(Skill.HITPOINTS);
		if (hitpoints != null)
		{
			GoalStep step = hitpointsStep(hitpoints, combatXp, profile, settings, itemNames);
			if (step != null)
			{
				skillSteps.add(step);
			}
		}

		// Quick levels first: they clear clutter and are often what gates everything else.
		skillSteps.sort(Comparator.comparingDouble(s -> s.getHours() == null ? Double.MAX_VALUE : s.getHours()));
		List<GoalStep> steps = new ArrayList<>(skillSteps);

		for (Quest quest : quests)
		{
			if (!profile.hasCompleted(quest))
			{
				steps.add(new GoalStep(GoalStep.Kind.QUEST, "Complete " + quest.getName(), null, null, null));
			}
		}

		if (profile.getQuestPoints() < questPoints)
		{
			steps.add(new GoalStep(GoalStep.Kind.QUEST,
				"Reach " + questPoints + " quest points (" + profile.getQuestPoints() + " now)", null, null, null));
		}

		for (int itemId : items)
		{
			if (profile.owns(itemId))
			{
				continue;
			}

			String name = itemNames == null ? null : itemNames.nameOf(itemId);
			String label = name != null ? name : "item " + itemId;

			// Without a bank snapshot, "you do not have this" would be a guess.
			steps.add(profile.isBankKnown()
				? new GoalStep(GoalStep.Kind.ITEM, "Get " + label, null, null, null)
				: new GoalStep(GoalStep.Kind.NOTE, "Have " + label + " (open your bank to check)", null, null, null));
		}

		for (String note : notes)
		{
			steps.add(new GoalStep(GoalStep.Kind.NOTE, note, null, null, null));
		}

		return steps;
	}

	/**
	 * Hitpoints rises by a third of the experience earned in the other combat skills,
	 * so training those for the same goal gets it much of the way there. Only the gap
	 * left after that is worth a step of its own; counting it all would double-count
	 * the same hours.
	 */
	@Nullable
	private GoalStep hitpointsStep(int level, long combatXp, PlayerProfile profile, EngineSettings settings,
								   @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		long gained = combatXp / 3;
		int xpAfter = (int) Math.min(Experience.MAX_SKILL_XP, profile.xp(Skill.HITPOINTS) + gained);
		int levelAfter = Math.max(profile.level(Skill.HITPOINTS), Experience.getLevelForXp(xpAfter));

		if (levelAfter >= level)
		{
			return gained > 0 ? null : trainStep(Skill.HITPOINTS, level, profile, settings, itemNames);
		}

		PlayerProfile afterCombat = profile.toBuilder()
			.level(Skill.HITPOINTS, levelAfter)
			.xp(Skill.HITPOINTS, xpAfter)
			.build();
		return trainStep(Skill.HITPOINTS, level, afterCombat, settings, itemNames);
	}

	/**
	 * A training step to the given level, or null when the player is already there.
	 */
	@Nullable
	private GoalStep trainStep(Skill skill, int level, PlayerProfile profile, EngineSettings settings,
							   @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		if (profile.level(skill) >= level)
		{
			return null;
		}

		Route route = estimator.route(skill, profile, level, RouteStyle.RECOMMENDED, settings, itemNames);
		String label = "Train " + Skills.displayName(skill) + " to " + level;
		Double hours = route.getLegs().isEmpty() ? null : route.getHours();

		return new GoalStep(GoalStep.Kind.SKILL, label, hours, skill, route);
	}

	private static GoalPlan finish(Goal goal, String title, List<GoalStep> steps, @Nullable Double progress)
	{
		double hours = 0;
		int untimed = 0;
		boolean complete = true;

		for (GoalStep step : steps)
		{
			if (step.getKind() != GoalStep.Kind.NOTE)
			{
				complete = false;
			}

			if (step.getHours() != null)
			{
				hours += step.getHours();
			}
			else if (step.getKind() != GoalStep.Kind.NOTE)
			{
				untimed++;
			}

			// A route that runs out of methods is only partly timed.
			if (step.getRoute() != null && !step.getRoute().isComplete() && step.getHours() != null)
			{
				untimed++;
			}
		}

		return new GoalPlan(goal, title, true, complete, Collections.unmodifiableList(steps),
			hours, untimed, complete ? Double.valueOf(1d) : progress);
	}

	private static GoalPlan unrecognised(Goal goal, String title)
	{
		return new GoalPlan(goal, title, false, false, Collections.emptyList(), 0, 0, null);
	}
}
