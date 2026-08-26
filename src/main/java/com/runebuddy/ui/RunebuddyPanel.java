package com.runebuddy.ui;

import com.runebuddy.RunebuddyConfig;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RecommendationEngine;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
public class RunebuddyPanel extends PluginPanel
{
	private final OverviewTab overview;
	private final SkillsTab skills;
	private final GearTab gear;

	private PlayerProfile profile = PlayerProfile.LOGGED_OUT;

	public RunebuddyPanel(RecommendationEngine engine, GearAdvisor gearAdvisor, ItemManager itemManager,
						  SkillIconManager skillIcons, RunebuddyConfig config)
	{
		super(false);

		PanelContext context = new PanelContext(engine, gearAdvisor, itemManager, skillIcons, config);

		overview = new OverviewTab(context);
		skills = new SkillsTab(context);
		gear = new GearTab(context);

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
		overview.update(profile);
		skills.update(profile);
		gear.update(profile);
	}
}
