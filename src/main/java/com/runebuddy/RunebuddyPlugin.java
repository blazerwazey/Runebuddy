package com.runebuddy;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.DataUpdater;
import com.runebuddy.engine.Advisors;
import com.runebuddy.engine.GoalStore;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.ProfileTracker;
import com.runebuddy.ui.RunebuddyPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
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
import okhttp3.OkHttpClient;

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
	/**
	 * How long to keep re-reading the account after logging in, in game ticks.
	 */
	private static final int SETTLE_TICKS = 10;

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

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OkHttpClient okHttpClient;

	/**
	 * How often to look for newer data while the client runs.
	 */
	private static final long UPDATE_INTERVAL_HOURS = 6;

	private RunebuddyPanel panel;
	private NavigationButton navigationButton;

	/**
	 * The data shipped in the jar, kept so switching updates off can return to it.
	 */
	private DataStore bundled;
	private ScheduledFuture<?> updateTask;

	/**
	 * Bumped whenever updates start or stop, so a fetch that finishes after the switch
	 * was turned off cannot sneak its data back in.
	 */
	private final AtomicInteger updateGeneration = new AtomicInteger();

	/**
	 * Set when something we care about changes; the snapshot is rebuilt at most once per
	 * tick rather than once per event, since a single bank visit fires a great many.
	 */
	private boolean dirty = true;

	/**
	 * Ticks left in which to keep re-reading the account after a login.
	 *
	 * <p>The first tick after logging in can land before every stat has arrived, and
	 * nothing marks the profile dirty again while the player stands still, so a single
	 * early read leaves the panel showing half an account indefinitely.
	 */
	private int settleTicks;

	@Override
	protected void startUp()
	{
		bundled = DataStore.load(gson);
		profileTracker.prime(bundled);

		panel = new RunebuddyPanel(
			new Advisors(bundled),
			new GoalStore(configManager, gson),
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

		if (config.updateData())
		{
			startUpdates();
		}
	}

	@Override
	protected void shutDown()
	{
		stopUpdates();
		clientToolbar.removeNavigation(navigationButton);
		profileTracker.reset();
		panel = null;
		navigationButton = null;
	}

	/**
	 * Applies the cached copy, then checks for newer data now and every few hours.
	 *
	 * <p>Fetching blocks, so it runs on OkHttp's own worker threads; the shared scheduler
	 * only kicks it off, and the client thread and the EDT never wait on the network.
	 */
	private void startUpdates()
	{
		stopUpdates();

		int generation = updateGeneration.get();
		File cache = new File(new File(RuneLite.RUNELITE_DIR, "runebuddy"), "data-cache.json");
		DataUpdater fresh = new DataUpdater(gson, DataUpdater.http(okHttpClient), cache,
			DataUpdater.bundledManifest(gson).getVersion(), data ->
		{
			if (updateGeneration.get() == generation)
			{
				useData(data);
			}
		});

		ExecutorService worker = okHttpClient.dispatcher().executorService();
		worker.submit(() ->
		{
			fresh.loadCache();
			fresh.refresh();
		});
		updateTask = executor.scheduleWithFixedDelay(() -> worker.submit(fresh::refresh),
			UPDATE_INTERVAL_HOURS, UPDATE_INTERVAL_HOURS, TimeUnit.HOURS);
	}

	private void stopUpdates()
	{
		if (updateTask != null)
		{
			updateTask.cancel(false);
			updateTask = null;
		}

		updateGeneration.incrementAndGet();
	}

	/**
	 * Swaps in a new data set. Called from a background thread.
	 */
	private void useData(DataStore data)
	{
		// The tracker's lists are read on the client thread, so they change there too.
		clientThread.invokeLater(() ->
		{
			profileTracker.prime(data);
			dirty = true;
		});

		RunebuddyPanel current = panel;
		if (current != null)
		{
			current.setAdvisors(new Advisors(data));
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (settleTicks > 0)
		{
			settleTicks--;
			dirty = true;
		}

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

		if (state == GameState.LOGGED_IN)
		{
			settleTicks = SETTLE_TICKS;
		}

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
		// The plugin's own saves (the bank snapshot, goals) land in the same group but
		// are not preferences; goal edits repaint themselves.
		if (RunebuddyConfig.GROUP.equals(event.getGroup())
			&& RunebuddyConfig.UPDATE_DATA_KEY.equals(event.getKey()) && panel != null)
		{
			if (config.updateData())
			{
				startUpdates();
			}
			else
			{
				stopUpdates();
				useData(bundled);
			}
		}

		if (RunebuddyConfig.GROUP.equals(event.getGroup()) && panel != null
			&& !ProfileTracker.BANK_SNAPSHOT_KEY.equals(event.getKey())
			&& !GoalStore.GOALS_KEY.equals(event.getKey()))
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
