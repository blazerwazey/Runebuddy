package com.runebuddy.ui;

import com.runebuddy.RunebuddyConfig;
import com.runebuddy.engine.EngineSettings;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.engine.ContentAdvisor;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RecommendationEngine;
import com.runebuddy.engine.RequirementReport;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
class PanelContext
{
	private final RecommendationEngine engine;
	private final GearAdvisor gearAdvisor;
	private final ContentAdvisor contentAdvisor;
	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;
	private final RunebuddyConfig config;

	RecommendationEngine engine()
	{
		return engine;
	}

	GearAdvisor gear()
	{
		return gearAdvisor;
	}

	ContentAdvisor content()
	{
		return contentAdvisor;
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
