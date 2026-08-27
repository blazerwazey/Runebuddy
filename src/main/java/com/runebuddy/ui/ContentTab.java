package com.runebuddy.ui;

import com.runebuddy.data.ContentCategory;
import com.runebuddy.engine.ContentAdvice;
import com.runebuddy.engine.ContentSuggestion;
import com.runebuddy.engine.PlayerProfile;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * What to go and do, given the account you have.
 *
 * <p>Where the Skills tab answers "how do I level this", this answers "what is worth my
 * evening". Some activities appear in both, and read differently in each.
 */
class ContentTab extends JPanel
{
	/**
	 * Locked content is worth a glimpse so there is something to aim at, but the list is
	 * long and mostly irrelevant, so only the nearest few are shown.
	 */
	private static final int LOCKED_PREVIEW = 5;

	private final PanelContext context;
	private final JPanel content = new JPanel();

	ContentTab(PanelContext context)
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
			error.setContent("Runebuddy", "Log in to see what you are ready for.");
			content.add(error);
			revalidate();
			repaint();
			return;
		}

		Set<ContentCategory> categories = context.contentCategories();
		if (categories.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing selected",
				"Every content category is switched off in the plugin settings.");
			content.add(error);
			revalidate();
			repaint();
			return;
		}

		ContentAdvice advice = context.content()
			.advise(profile, categories, context.itemNames(profile));

		if (advice.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing here yet",
				"No activity in the data set fits this account.");
			content.add(error);
			revalidate();
			repaint();
			return;
		}

		addSection("Ready now", advice.getReady(), Integer.MAX_VALUE);
		addSection("Nearly there", advice.getClose(), Integer.MAX_VALUE);
		addSection("Something to aim at", advice.getLocked(), LOCKED_PREVIEW);

		revalidate();
		repaint();
	}

	private void addSection(String title, List<ContentSuggestion> suggestions, int limit)
	{
		if (suggestions.isEmpty())
		{
			return;
		}

		content.add(heading(title));

		int shown = 0;
		for (ContentSuggestion suggestion : suggestions)
		{
			if (shown++ >= limit)
			{
				break;
			}

			content.add(new ContentCard(suggestion));
		}
	}

	private static JLabel heading(String text)
	{
		JLabel heading = UiUtils.title(text);
		heading.setBorder(UiUtils.padding(10, 6, 4, 6));
		UiUtils.capHeight(heading);
		return heading;
	}
}
