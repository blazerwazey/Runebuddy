package com.runebuddy.ui;

import com.runebuddy.engine.MethodScore;
import com.runebuddy.engine.PlayerProfile;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * The "just tell me what to do" view: a snapshot of the account, then the strongest
 * suggestion from each of a handful of skills.
 */
class OverviewTab extends JPanel
{
	private static final int SUGGESTIONS = 6;

	private final PanelContext context;
	private final JPanel content = new JPanel();

	OverviewTab(PanelContext context)
	{
		this.context = context;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);
	}

	void update(PlayerProfile profile)
	{
		content.removeAll();

		if (!profile.isLoggedIn())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Runebuddy", "Log in and your training plan will appear here.");
			content.add(error);
			revalidate();
			repaint();
			return;
		}

		content.add(header(profile));

		List<MethodScore> suggestions = context.engine()
			.topOverall(profile, context.settings(), context.itemNames(profile), SUGGESTIONS);

		if (suggestions.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing to suggest",
				"No training method in the data set fits this account yet.");
			content.add(error);
		}
		else
		{
			content.add(sectionHeading("Best use of your next hour"));
			for (MethodScore score : suggestions)
			{
				content.add(new MethodCard(score, true));
			}
		}

		revalidate();
		repaint();
	}

	private static JPanel header(PlayerProfile profile)
	{
		JPanel header = new JPanel(new GridLayout(1, 2, 4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(UiUtils.padding(8, 8, 8, 8));

		JLabel combat = UiUtils.title("Combat " + profile.combatLevel());
		header.add(combat);

		JLabel total = new JLabel("Total " + profile.totalLevel(), SwingConstants.RIGHT);
		total.setFont(UiUtils.smallBold());
		total.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(total);

		UiUtils.capHeight(header);
		return header;
	}

	private static JLabel sectionHeading(String text)
	{
		JLabel heading = UiUtils.title(text);
		heading.setBorder(UiUtils.padding(10, 6, 4, 6));
		UiUtils.capHeight(heading);
		return heading;
	}
}
