package com.runebuddy.engine;

import com.runebuddy.data.TrainingMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Turns experience rates into hours.
 *
 * <p>Rates in the data change with level, and the XP needed per level grows about 10%
 * each level, so a single "XP left ÷ rate" would be badly wrong over any real distance.
 * Everything here walks level by level instead, using the rate at that level and
 * switching method as better ones unlock.
 */
public class TimeEstimator
{
	public static final int MAX_LEVEL = 99;

	private final RecommendationEngine engine;

	public TimeEstimator(RecommendationEngine engine)
	{
		this.engine = engine;
	}

	/**
	 * Hours to take one method from the given experience to the start of a target level,
	 * assuming the player sticks with it throughout. Only the rest of the current level
	 * counts, not the part already trained.
	 *
	 * @return hours, 0 if already there, or infinity if the method gives no experience
	 */
	public static double hoursToLevel(TrainingMethod method, int currentXp, int targetLevel)
	{
		int target = Math.min(targetLevel, MAX_LEVEL);
		int level = Experience.getLevelForXp(currentXp);
		double hours = 0;

		for (int l = level; l < target; l++)
		{
			int rate = method.xpAt(l);
			if (rate <= 0)
			{
				return Double.POSITIVE_INFINITY;
			}

			hours += (double) xpLeftInLevel(l, currentXp) / rate;
		}

		return hours;
	}

	/**
	 * Plans a route from the player's current level to the target in the given style.
	 *
	 * @param itemNames resolves item ids to names for requirement checks, may be null
	 */
	public Route route(Skill skill, PlayerProfile profile, int targetLevel, RouteStyle style,
					   EngineSettings settings, @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		int from = profile.level(skill);
		int target = Math.min(targetLevel, MAX_LEVEL);
		int currentXp = Math.max(profile.xp(skill), Experience.getXpForLevel(from));

		// The ranking only needs its top pick here; skipping the unlock preview saves work
		// on every level of the walk.
		EngineSettings quiet = settings.toBuilder().showUnlocksSoon(false).methodsPerSkill(1).build();

		List<RouteLeg> legs = new ArrayList<>();
		TrainingMethod current = null;
		int legStart = from;
		double legHours = 0;
		double legGold = 0;
		double totalHours = 0;
		double totalGold = 0;
		Integer blockedAt = null;

		for (int level = from; level < target; level++)
		{
			PlayerProfile atLevel = level == from ? profile : profile.toBuilder().level(skill, level).build();
			TrainingMethod pick = pick(skill, atLevel, level, style, quiet, itemNames);
			if (pick == null)
			{
				blockedAt = level;
				break;
			}

			if (current != null && pick != current)
			{
				legs.add(new RouteLeg(current, legStart, level, legHours, Math.round(legGold)));
				legStart = level;
				legHours = 0;
				legGold = 0;
			}

			current = pick;
			double hours = (double) xpLeftInLevel(level, currentXp) / pick.xpAt(level);
			double gold = hours * pick.getGpPerHour();
			legHours += hours;
			legGold += gold;
			totalHours += hours;
			totalGold += gold;
		}

		if (current != null)
		{
			int legEnd = blockedAt != null ? blockedAt : target;
			legs.add(new RouteLeg(current, legStart, legEnd, legHours, Math.round(legGold)));
		}

		return new Route(skill, style, from, target, Collections.unmodifiableList(legs),
			totalHours, Math.round(totalGold), blockedAt);
	}

	/**
	 * Routes to the target in every style, with identical routes merged, in the order
	 * the styles are declared.
	 */
	public List<RouteOption> alternatives(Skill skill, PlayerProfile profile, int targetLevel,
										  EngineSettings settings,
										  @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		Map<List<String>, List<RouteStyle>> stylesByPath = new LinkedHashMap<>();
		Map<List<String>, Route> routeByPath = new LinkedHashMap<>();

		for (RouteStyle style : RouteStyle.values())
		{
			Route route = route(skill, profile, targetLevel, style, settings, itemNames);
			List<String> path = path(route);
			stylesByPath.computeIfAbsent(path, k -> new ArrayList<>()).add(style);
			routeByPath.putIfAbsent(path, route);
		}

		List<RouteOption> options = new ArrayList<>();
		for (Map.Entry<List<String>, Route> entry : routeByPath.entrySet())
		{
			options.add(new RouteOption(
				Collections.unmodifiableList(stylesByPath.get(entry.getKey())), entry.getValue()));
		}

		return options;
	}

	/**
	 * What makes two routes the same: the same methods over the same levels.
	 */
	private static List<String> path(Route route)
	{
		List<String> path = new ArrayList<>();
		for (RouteLeg leg : route.getLegs())
		{
			path.add(leg.getMethod().getId() + "@" + leg.getFromLevel() + "-" + leg.getToLevel());
		}

		path.add("blocked@" + route.getBlockedAtLevel());
		return path;
	}

	/**
	 * Experience still needed to finish the given level, given where the player is now.
	 */
	private static int xpLeftInLevel(int level, int currentXp)
	{
		int start = Math.max(Experience.getXpForLevel(level), currentXp);
		return Math.max(0, Experience.getXpForLevel(level + 1) - start);
	}

	@Nullable
	private TrainingMethod pick(Skill skill, PlayerProfile profile, int level, RouteStyle style,
								EngineSettings settings, @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		if (style == RouteStyle.RECOMMENDED)
		{
			for (MethodScore score : engine.adviceFor(skill, profile, settings, itemNames).getRecommended())
			{
				if (score.getMethod().xpAt(level) > 0)
				{
					return score.getMethod();
				}
			}

			return null;
		}

		// Methods the player has out-levelled only count when nothing else is left.
		// Without this an extreme like "cheapest" happily plans 1,800 hours of chickens.
		List<TrainingMethod> candidates = new ArrayList<>();
		List<TrainingMethod> outgrown = new ArrayList<>();
		for (TrainingMethod method : engine.availableNow(skill, profile, itemNames))
		{
			if (method.xpAt(level) > 0)
			{
				(method.isOutgrown(level) ? outgrown : candidates).add(method);
			}
		}

		if (candidates.isEmpty())
		{
			candidates = outgrown;
		}

		if (candidates.isEmpty())
		{
			return null;
		}

		Comparator<TrainingMethod> faster = Comparator.comparingInt(m -> m.xpAt(level));
		Comparator<TrainingMethod> goldPerXp = Comparator.comparingDouble(m -> (double) m.getGpPerHour() / m.xpAt(level));

		switch (style)
		{
			case FASTEST:
				return Collections.max(candidates, faster);

			case PROFITABLE:
				// Gold per hour, the figure shown everywhere else. Gold per experience
				// would favour the slowest money maker and plan absurdly long routes.
				return Collections.max(candidates,
					Comparator.comparingInt(TrainingMethod::getGpPerHour).thenComparing(faster));

			case CHEAPEST:
				// Anything free or profitable costs nothing, so the fastest of those wins.
				// When everything costs, the least gold per experience point does.
				List<TrainingMethod> free = new ArrayList<>();
				for (TrainingMethod method : candidates)
				{
					if (method.getGpPerHour() >= 0)
					{
						free.add(method);
					}
				}

				return free.isEmpty()
					? Collections.max(candidates, goldPerXp.thenComparing(faster))
					: Collections.max(free, faster);

			case AFK:
				return Collections.max(candidates,
					Comparator.comparingDouble((TrainingMethod m) -> m.getEffort().getWeight()).thenComparing(faster));

			default:
				throw new IllegalArgumentException("unhandled route style " + style);
		}
	}
}
