package com.runebuddy.engine;

import com.runebuddy.data.DataStore;
import com.runebuddy.data.Skills;
import com.runebuddy.data.TrainingMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Skill;

/**
 * Ranks training methods for a given account.
 *
 * <p>Three things are weighed against each other, with the user setting how much each
 * matters: experience per hour at their level, what the method costs or earns, and how
 * much attention it demands. The experience term is normalised against the other
 * candidates for the same skill, so "fast" always means "fast compared to the
 * alternatives you actually have", not against some absolute ceiling.
 */
public class RecommendationEngine
{
	/**
	 * How far short of the requirements a method can be and still be worth previewing.
	 * Roughly "a couple of levels, or one quest".
	 */
	private static final int UNLOCK_HORIZON = 10;

	/**
	 * Multiplier applied once the player has passed a method's useful range. It stays
	 * listed — it does still work — but drops below anything level-appropriate.
	 */
	private static final double OUTGROWN_PENALTY = 0.6;

	private final DataStore data;

	public RecommendationEngine(DataStore data)
	{
		this.data = data;
	}

	/**
	 * Ranks every method for one skill.
	 *
	 * @param itemNames resolves item ids to names for requirement labels, may be null
	 */
	public SkillAdvice adviceFor(Skill skill, PlayerProfile profile, EngineSettings settings,
								 @Nullable RequirementReport.ItemNameResolver itemNames)
	{
		int level = profile.level(skill);

		List<TrainingMethod> available = new ArrayList<>();
		List<TrainingMethod> soon = new ArrayList<>();
		List<RequirementReport> soonReports = new ArrayList<>();

		for (TrainingMethod method : data.methodsFor(skill))
		{
			if (!isPossible(method, profile))
			{
				continue;
			}

			RequirementReport report = RequirementReport.check(method.getRequirements(), profile, itemNames);
			boolean levelMet = level >= method.getMinLevel();

			if (levelMet && report.isSatisfied())
			{
				available.add(method);
			}
			else if (settings.isShowUnlocksSoon())
			{
				int levelsShort = Math.max(0, method.getMinLevel() - level);
				if (levelsShort + report.getDistance() <= UNLOCK_HORIZON)
				{
					soon.add(method);
					soonReports.add(report);
				}
			}
		}

		List<MethodScore> recommended = score(available, profile, settings, level, itemNames);
		recommended.sort(Comparator.comparingDouble(MethodScore::getScore).reversed());

		int limit = Math.min(settings.getMethodsPerSkill(), recommended.size());
		recommended = new ArrayList<>(recommended.subList(0, limit));

		List<MethodScore> unlocking = new ArrayList<>();
		for (int i = 0; i < soon.size(); i++)
		{
			TrainingMethod method = soon.get(i);
			RequirementReport report = soonReports.get(i);
			int previewLevel = Math.max(level, method.getMinLevel());

			unlocking.add(new MethodScore(
				method,
				0d,
				method.xpAt(previewLevel),
				method.getGpPerHour(),
				report,
				false,
				unlockRationale(method, level, report)));
		}

		unlocking.sort(Comparator.comparingInt(m ->
			Math.max(0, m.getMethod().getMinLevel() - level) + m.getRequirements().getDistance()));

		return new SkillAdvice(skill, level,
			Collections.unmodifiableList(recommended),
			Collections.unmodifiableList(unlocking));
	}

	/**
	 * The single strongest suggestion for each skill, ranked across all skills.
	 *
	 * <p>Skills the player has barely touched are nudged up: an hour spent on a level 20
	 * skill moves an account further than an hour spent on a level 95 one.
	 */
	public List<MethodScore> topOverall(PlayerProfile profile, EngineSettings settings,
										@Nullable RequirementReport.ItemNameResolver itemNames, int limit)
	{
		List<ScoredSuggestion> best = new ArrayList<>();

		for (Skill skill : Skills.trainable())
		{
			SkillAdvice advice = adviceFor(skill, profile, settings, itemNames);
			if (advice.getRecommended().isEmpty())
			{
				continue;
			}

			MethodScore top = advice.getRecommended().get(0);
			best.add(new ScoredSuggestion(top, top.getScore() * lowLevelBias(advice.getLevel())));
		}

		best.sort(Comparator.comparingDouble((ScoredSuggestion s) -> s.weighted).reversed());

		List<MethodScore> result = new ArrayList<>();
		for (ScoredSuggestion suggestion : best.subList(0, Math.min(limit, best.size())))
		{
			result.add(suggestion.score);
		}

		return result;
	}

	/**
	 * Whether the account can do this method at all, regardless of level. Methods it can
	 * never do are dropped rather than shown as locked, since no amount of training
	 * unlocks them.
	 */
	private boolean isPossible(TrainingMethod method, PlayerProfile profile)
	{
		if (method.isMembers() && !profile.isMembers())
		{
			return false;
		}

		return method.isIronmanFriendly() || !profile.isIronman();
	}

	private List<MethodScore> score(List<TrainingMethod> candidates, PlayerProfile profile,
									EngineSettings settings, int level,
									@Nullable RequirementReport.ItemNameResolver itemNames)
	{
		List<MethodScore> scored = new ArrayList<>();
		if (candidates.isEmpty())
		{
			return scored;
		}

		int bestXp = 1;
		int worstCost = 0;
		for (TrainingMethod method : candidates)
		{
			bestXp = Math.max(bestXp, method.xpAt(level));
			worstCost = Math.max(worstCost, -method.getGpPerHour());
		}

		double wXp = settings.effectiveXpWeight();
		double wGp = settings.effectiveGpWeight();
		double wAfk = settings.effectiveAfkWeight();
		double total = settings.totalWeight();

		for (TrainingMethod method : candidates)
		{
			int xpPerHour = method.xpAt(level);
			double xpTerm = (double) xpPerHour / bestXp;
			double gpTerm = goldScore(method, profile, settings, worstCost);
			double afkTerm = method.getEffort().getWeight();

			double raw = (wXp * xpTerm + wGp * gpTerm + wAfk * afkTerm) / total;
			boolean outgrown = method.isOutgrown(level);
			if (outgrown)
			{
				raw *= OUTGROWN_PENALTY;
			}

			scored.add(new MethodScore(
				method,
				raw,
				xpPerHour,
				method.getGpPerHour(),
				RequirementReport.check(method.getRequirements(), profile, itemNames),
				outgrown,
				rationale(method, outgrown, xpTerm, gpTerm, afkTerm, wXp, wGp, wAfk)));
		}

		return scored;
	}

	/**
	 * How comfortably the account can sustain this method, in 0..1.
	 *
	 * <p>Profitable and free methods score full marks, as does anything the player can
	 * fund for their whole budget. Below that the term blends two things: how much of
	 * the budget they can actually cover, and how this method's cost compares with the
	 * other options. The second half matters because when a player cannot afford
	 * anything, both a 2k/hr method and a 120k/hr one score near zero on affordability
	 * alone — yet one of them is plainly the answer.
	 *
	 * <p>When we have never seen the player's bank we cannot judge their wealth at all,
	 * so cost is ranked purely relative to the alternatives.
	 */
	private double goldScore(TrainingMethod method, PlayerProfile profile, EngineSettings settings, int worstCost)
	{
		int costPerHour = -method.getGpPerHour();
		if (costPerHour <= 0)
		{
			return 1d;
		}

		double relative = worstCost > 0 ? clamp(1d - (double) costPerHour / worstCost) : 1d;

		if (!profile.isBankKnown())
		{
			return relative;
		}

		double hoursAffordable = (double) profile.getLiquidGp() / costPerHour;
		double budgetCovered = clamp(hoursAffordable / Math.max(1, settings.getBudgetHours()));

		return budgetCovered >= 1d ? 1d : (budgetCovered + relative) / 2d;
	}

	/**
	 * Turns the winning term into a sentence. The user sees this instead of the score.
	 */
	private String rationale(TrainingMethod method, boolean outgrown,
							 double xpTerm, double gpTerm, double afkTerm,
							 double wXp, double wGp, double wAfk)
	{
		if (outgrown)
		{
			return "Still works, but you have out-levelled it";
		}

		double xpContribution = wXp * xpTerm;
		double gpContribution = wGp * gpTerm;
		double afkContribution = wAfk * afkTerm;

		if (xpContribution >= gpContribution && xpContribution >= afkContribution)
		{
			return xpTerm >= 0.999
				? "Fastest experience at your level"
				: "Strong experience for what it asks of you";
		}

		if (gpContribution >= afkContribution)
		{
			return method.getGpPerHour() > 0
				? "Makes money while you train"
				: "Cheap to keep going";
		}

		return method.getEffort().getLabel() + ", so it fits around other things";
	}

	private String unlockRationale(TrainingMethod method, int level, RequirementReport report)
	{
		int levelsShort = method.getMinLevel() - level;
		if (levelsShort > 0 && report.isSatisfied())
		{
			return levelsShort == 1
				? "One level away"
				: levelsShort + " levels away";
		}

		String blocking = report.blockingSummary();
		if (levelsShort > 0 && !blocking.isEmpty())
		{
			return levelsShort + " levels away, and " + blocking;
		}

		return blocking.isEmpty() ? "Almost available" : Character.toUpperCase(blocking.charAt(0)) + blocking.substring(1);
	}

	/**
	 * 1.0 for an untrained skill down to 0.5 at 99, so low skills surface first on the
	 * overview without drowning out a genuinely better option elsewhere.
	 */
	private static double lowLevelBias(int level)
	{
		return 0.5 + 0.5 * (99 - Math.min(level, 99)) / 98d;
	}

	private static double clamp(double value)
	{
		return Math.max(0d, Math.min(1d, value));
	}

	private static final class ScoredSuggestion
	{
		private final MethodScore score;
		private final double weighted;

		private ScoredSuggestion(MethodScore score, double weighted)
		{
			this.score = score;
			this.weighted = weighted;
		}
	}
}
