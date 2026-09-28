package com.runebuddy.ui;

import com.runebuddy.RunebuddyConfig;
import com.runebuddy.engine.EngineSettings;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.engine.ContentAdvisor;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RecommendationEngine;
import com.runebuddy.engine.RequirementReport;
import com.runebuddy.engine.TimeEstimator;
import com.runebuddy.engine.Advisors;
import com.runebuddy.engine.Goal;
import com.runebuddy.engine.GoalPlanner;
import com.runebuddy.engine.GoalStore;
import com.runebuddy.engine.SlayerAdvisor;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * What the tabs need from the rest of the plugin.
 *
 * <p>Item prices and images come from the client's own caches. Those are safe to read
 * from the EDT — {@link ItemManager} is thread-safe and {@link AsyncBufferedImage} paints
 * itself in when the sprite arrives — which is why the panel can render without hopping
 * back to the client thread.
 */
class PanelContext
{
	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;
	private final RunebuddyConfig config;
	private final GoalStore goalStore;

	/**
	 * Replaced whole when the data changes. Only read and written on the EDT.
	 */
	private Advisors advisors;

	/**
	 * Called after a goal is added or removed, so every tab can reflect it.
	 */
	private final Runnable onGoalsChanged;

	PanelContext(Advisors advisors, GoalStore goalStore, ItemManager itemManager,
				 SkillIconManager skillIcons, RunebuddyConfig config, Runnable onGoalsChanged)
	{
		this.advisors = advisors;
		this.goalStore = goalStore;
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		this.config = config;
		this.onGoalsChanged = onGoalsChanged;
	}

	void setAdvisors(Advisors advisors)
	{
		this.advisors = advisors;
	}

	RecommendationEngine engine()
	{
		return advisors.getEngine();
	}

	TimeEstimator estimator()
	{
		return advisors.getEstimator();
	}

	GoalPlanner goalPlanner()
	{
		return advisors.getGoals();
	}

	SlayerAdvisor slayer()
	{
		return advisors.getSlayer();
	}

	GearAdvisor gear()
	{
		return advisors.getGear();
	}

	ContentAdvisor content()
	{
		return advisors.getContent();
	}

	GoalStore goals()
	{
		return goalStore;
	}

	/**
	 * Adds a goal and repaints.
	 *
	 * @return false when there is no account to save against or the list is full
	 */
	boolean addGoal(Goal goal)
	{
		boolean added = goalStore.add(goal);
		if (added)
		{
			onGoalsChanged.run();
		}

		return added;
	}

	void removeGoal(Goal goal)
	{
		goalStore.remove(goal);
		onGoalsChanged.run();
	}

	/**
	 * Which activity categories the user wants listed.
	 */
	Set<ContentCategory> contentCategories()
	{
		Set<ContentCategory> categories = EnumSet.noneOf(ContentCategory.class);
		if (config.showBosses())
		{
			categories.add(ContentCategory.BOSS);
		}
		if (config.showRaids())
		{
			categories.add(ContentCategory.RAID);
		}
		if (config.showMinigames())
		{
			categories.add(ContentCategory.MINIGAME);
		}
		if (config.showSkillingContent())
		{
			categories.add(ContentCategory.SKILLING);
		}
		if (config.showQuests())
		{
			categories.add(ContentCategory.QUEST);
		}
		if (config.showDiaries())
		{
			categories.add(ContentCategory.DIARY);
		}
		if (config.showUnlocks())
		{
			categories.add(ContentCategory.UNLOCK);
		}

		return categories;
	}

	RunebuddyConfig config()
	{
		return config;
	}

	/**
	 * The current ranking preferences, read fresh so config changes take effect on the
	 * next repaint.
	 */
	EngineSettings settings()
	{
		return EngineSettings.builder()
			.xpWeight(config.xpWeight())
			.gpWeight(config.gpWeight())
			.afkWeight(config.afkWeight())
			.methodsPerSkill(config.methodsPerSkill())
			.budgetHours(config.budgetHours())
			.showUnlocksSoon(config.showUnlocksSoon())
			.build();
	}

	/**
	 * Resolves item ids to names for requirement labels, from the snapshot rather than
	 * the client.
	 */
	RequirementReport.ItemNameResolver itemNames(PlayerProfile profile)
	{
		return profile.itemNameResolver();
	}

	/**
	 * Resolves prices for the gear advisor, so "buy next" stays within what the player
	 * can actually pay. Null when the user has turned prices off, which makes the
	 * advisor fall back to judging on requirements alone.
	 */
	@Nullable
	GearAdvisor.PriceResolver prices(PlayerProfile profile)
	{
		return config.useLivePrices() ? profile.priceResolver() : null;
	}

	/**
	 * Current Grand Exchange price, or 0 when prices are switched off or unknown.
	 */
	int priceOf(PlayerProfile profile, int itemId)
	{
		return config.useLivePrices() ? profile.priceOf(itemId) : 0;
	}

	@Nullable
	AsyncBufferedImage itemImage(int itemId)
	{
		try
		{
			return itemManager.getImage(itemId);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	@Nullable
	BufferedImage skillIcon(Skill skill)
	{
		try
		{
			return skillIcons.getSkillImage(skill, true);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
