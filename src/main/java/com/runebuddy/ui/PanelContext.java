package com.runebuddy.ui;

import com.runebuddy.RunebuddyConfig;
import com.runebuddy.engine.EngineSettings;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.RecommendationEngine;
import com.runebuddy.engine.RequirementReport;
import java.awt.image.BufferedImage;
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
	 * Resolves item ids to names for requirement labels.
	 */
	RequirementReport.ItemNameResolver itemNames()
	{
		return itemId ->
		{
			try
			{
				return itemManager.getItemComposition(itemId).getName();
			}
			catch (RuntimeException e)
			{
				// An id the cache does not know about should not take the panel down.
				return null;
			}
		};
	}

	/**
	 * Resolves prices for the gear advisor, so "buy next" stays within what the player
	 * can actually pay. Null when the user has turned prices off, which makes the
	 * advisor fall back to judging on requirements alone.
	 */
	@Nullable
	GearAdvisor.PriceResolver prices()
	{
		return config.useLivePrices() ? this::priceOf : null;
	}

	/**
	 * Current Grand Exchange price, or 0 when prices are switched off or unknown.
	 */
	int priceOf(int itemId)
	{
		if (!config.useLivePrices())
		{
			return 0;
		}

		try
		{
			return itemManager.getItemPrice(itemId);
		}
		catch (RuntimeException e)
		{
			return 0;
		}
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
