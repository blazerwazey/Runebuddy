package com.runebuddy;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.runebuddy.data.DataStore;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.ProfileTracker;
import com.runebuddy.engine.RecommendationEngine;
import com.runebuddy.ui.RunebuddyPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Tells a player what to train next and what gear to work toward, based on the account
 * they are actually logged into.
 */
@Slf4j
@PluginDescriptor(
	name = "Runebuddy",
	description = "Recommends the best training methods and gear upgrades for your account",
	tags = {"training", "skilling", "gear", "progress", "advisor"}
)
public class RunebuddyPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ProfileTracker profileTracker;

	@Inject
	private RunebuddyConfig config;

	@Inject
	private Gson gson;

	private RunebuddyPanel panel;
	private NavigationButton navigationButton;

	/**
	 * Set when something we care about changes; the snapshot is rebuilt at most once per
	 * tick rather than once per event, since a single bank visit fires a great many.
	 */
	private boolean dirty = true;

	@Override
	protected void startUp()
	{
		DataStore data = DataStore.load(gson);
		profileTracker.prime(data);

		panel = new RunebuddyPanel(
			new RecommendationEngine(data),
			new GearAdvisor(data),
			itemManager,
			skillIconManager,
			config);

		BufferedImage icon = ImageUtil.loadImageResource(RunebuddyPlugin.class, "/com/runebuddy/panel_icon.png");
		navigationButton = NavigationButton.builder()
			.tooltip("Runebuddy")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
		dirty = true;
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		profileTracker.reset();
		panel = null;
		navigationButton = null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!dirty || panel == null)
		{
			return;
		}

		dirty = false;

		// Runs on the client thread, which is the only place client state may be read.
		PlayerProfile profile = profileTracker.snapshot(config);
		panel.setProfile(profile);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// A different account may be about to log in, so the cached bank is no
			// longer known to belong to whoever we see next.
			profileTracker.reset();
			if (panel != null)
			{
				panel.setProfile(PlayerProfile.LOGGED_OUT);
			}
		}

		dirty = true;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		dirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			profileTracker.onBankChanged(event.getItemContainer());
		}

		dirty = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (RunebuddyConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			// The weights changed rather than the account, so re-rank what we already
			// have instead of waiting for the next tick.
			panel.refreshLater();
		}
	}

	@Provides
	RunebuddyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RunebuddyConfig.class);
	}
}
