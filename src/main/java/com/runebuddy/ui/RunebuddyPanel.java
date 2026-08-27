package com.runebuddy.ui;

import com.runebuddy.RunebuddyConfig;
import com.runebuddy.engine.ContentAdvisor;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RecommendationEngine;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The Runebuddy side panel.
 *
 * <p>Everything here runs on the Swing event dispatch thread and reads only the
 * immutable {@link PlayerProfile} it is handed, never the client.
 */
@Slf4j
public class RunebuddyPanel extends PluginPanel
{
	private final OverviewTab overview;
	private final SkillsTab skills;
	private final GearTab gear;
	private final ContentTab content;

	private PlayerProfile profile = PlayerProfile.LOGGED_OUT;

	public RunebuddyPanel(RecommendationEngine engine, GearAdvisor gearAdvisor,
						  ContentAdvisor contentAdvisor, ItemManager itemManager,
						  SkillIconManager skillIcons, RunebuddyConfig config)
	{
		super(false);

		PanelContext context = new PanelContext(engine, gearAdvisor, contentAdvisor,
			itemManager, skillIcons, config);

		overview = new OverviewTab(context);
		skills = new SkillsTab(context);
		gear = new GearTab(context);
		content = new ContentTab(context);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(UiUtils.padding(6, 6, 0, 6));

		MaterialTab overviewTab = new MaterialTab("Plan", tabs, overview);
		tabs.addTab(overviewTab);
		tabs.addTab(new MaterialTab("Skills", tabs, skills));
		tabs.addTab(new MaterialTab("Gear", tabs, gear));
		tabs.addTab(new MaterialTab("Do", tabs, content));
		tabs.select(overviewTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	/**
	 * Replaces the account state the panel is showing.
	 *
	 * <p>Safe to call from any thread; the repaint is marshalled onto the EDT.
	 */
	public void setProfile(PlayerProfile profile)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.profile = profile;
			refresh();
		});
	}

	/**
	 * Re-renders with the profile already held, for when the config changes rather than
	 * the account.
	 */
	public void refreshLater()
	{
		SwingUtilities.invokeLater(this::refresh);
	}

	private void refresh()
	{
		// Each tab is updated independently. Letting one failure escape used to leave the
		// other tabs showing whatever they had last time, which reads as three tabs
		// disagreeing about whether you are even logged in.
		update("Plan", () -> overview.update(profile));
		update("Skills", () -> skills.update(profile));
		update("Gear", () -> gear.update(profile));
		update("Do", () -> content.update(profile));
	}

	private static void update(String tab, Runnable action)
	{
		try
		{
			action.run();
		}
		catch (RuntimeException | Error e)
		{
			log.error("Runebuddy: the {} tab failed to render", tab, e);
		}
	}
}
