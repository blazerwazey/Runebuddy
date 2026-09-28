package com.runebuddy.ui;

import com.runebuddy.data.SlayerTaskInfo;
import com.runebuddy.engine.SlayerAdvice;
import com.runebuddy.engine.SlayerTask;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.LinkBrowser;

/**
 * The current Slayer task: how far through it the player is, and what to do about it.
 */
class SlayerTaskCard extends JPanel
{
	SlayerTaskCard(SlayerAdvice advice)
	{
		SlayerTask task = advice.getTask();
		SlayerTaskInfo info = advice.getInfo();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 1, 0, ColorScheme.BRAND_ORANGE),
			UiUtils.padding(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		String title = "Slayer: " + task.getName();
		JLabel heading = UiUtils.title(UiUtils.ellipsise(title, 30));
		heading.setToolTipText(title);
		body.add(wrap(heading));

		body.add(progress(task));

		Color verdictColor = info == null ? ColorScheme.MEDIUM_GRAY_COLOR
			: advice.isSkipNow() ? UiUtils.PENDING
			: ColorScheme.LIGHT_GRAY_COLOR;
		body.add(wrap(wrapped(advice.getVerdict(), verdictColor)));

		if (!advice.getLocations().isEmpty())
		{
			body.add(wrap(wrapped("Go to " + advice.getLocations().get(0), Color.WHITE)));
			if (advice.getLocations().size() > 1)
			{
				List<String> rest = advice.getLocations().subList(1, advice.getLocations().size());
				body.add(wrap(wrapped("or " + String.join(", ", rest), ColorScheme.MEDIUM_GRAY_COLOR)));
			}
		}

		if (info != null)
		{
			List<String> traits = new ArrayList<>();
			if (info.getStyle() != null)
			{
				traits.add(info.getStyle().getLabel());
			}
			traits.add(info.isCannonable() ? "cannon works" : "no cannon");
			body.add(wrap(UiUtils.muted(String.join(" · ", traits))));

			if (info.getNotes() != null)
			{
				body.add(wrap(wrapped(info.getNotes(), ColorScheme.MEDIUM_GRAY_COLOR)));
			}

			if (info.getWikiUrl() != null)
			{
				body.add(wrap(wikiLink(info.getWikiUrl())));
			}
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	private static JPanel progress(SlayerTask task)
	{
		ProgressBar bar = new ProgressBar();
		int assigned = Math.max(1, task.getAssigned());
		bar.setMaximumValue(assigned);
		bar.setValue(assigned - task.getRemaining());
		bar.setCenterLabel(task.getRemaining() + " left");
		bar.setLeftLabel("");
		bar.setRightLabel("");
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setForeground(ColorScheme.BRAND_ORANGE);
		bar.setPreferredSize(new Dimension(0, 14));

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setBorder(UiUtils.padding(3, 0, 3, 0));
		wrap.add(bar, BorderLayout.CENTER);
		UiUtils.capHeight(wrap);
		return wrap;
	}

	private static JLabel wrapped(String text, Color color)
	{
		JLabel label = UiUtils.body("<html><body style='width:170px'>" + escape(text) + "</body></html>");
		label.setForeground(color);
		return label;
	}

	private static JPanel wrap(JLabel label)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);
		UiUtils.capHeight(row);
		return row;
	}

	private static JLabel wikiLink(String url)
	{
		JLabel link = UiUtils.body("Read the wiki page");
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}
		});
		return link;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
