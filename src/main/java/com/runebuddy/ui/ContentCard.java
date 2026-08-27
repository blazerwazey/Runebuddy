package com.runebuddy.ui;

import com.runebuddy.data.ContentActivity;
import com.runebuddy.engine.ContentSuggestion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * One activity: what it is, what it gives you, and what is stopping you.
 */
class ContentCard extends JPanel
{
	ContentCard(ContentSuggestion suggestion)
	{
		ContentActivity activity = suggestion.getActivity();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = UiUtils.title(UiUtils.ellipsise(activity.getName(), 30));
		name.setToolTipText(activity.getName());
		body.add(name);

		// The rewards are the reason to go, so they lead.
		JLabel rewards = UiUtils.body("<html><body style='width:150px'>"
			+ escape(activity.getRewards()) + "</body></html>");
		rewards.setForeground(ColorScheme.BRAND_ORANGE);
		body.add(rewards);

		if (!suggestion.getRequirements().getStatuses().isEmpty())
		{
			body.add(new RequirementChip(suggestion.getRequirements().getStatuses()));
		}

		if (suggestion.getGearAdvice() != null)
		{
			JLabel gear = UiUtils.muted("<html><body style='width:150px'>"
				+ escape(suggestion.getGearAdvice()) + "</body></html>");
			gear.setForeground(UiUtils.PENDING);
			body.add(gear);
		}

		if (activity.getNotes() != null)
		{
			body.add(UiUtils.muted("<html><body style='width:150px'>"
				+ escape(activity.getNotes()) + "</body></html>"));
		}

		if (activity.getWikiUrl() != null)
		{
			body.add(wikiLink(activity.getWikiUrl()));
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	private static JLabel wikiLink(String url)
	{
		JLabel link = UiUtils.body("Read the wiki guide");
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(ColorScheme.BRAND_ORANGE);
			}
		});

		return link;
	}

	/**
	 * Text is rendered as HTML so it wraps in the narrow panel, so markup in the data
	 * file has to be neutralised first.
	 */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
