package com.runebuddy.ui;

import com.runebuddy.engine.NextAction;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

/**
 * One line of the "what next" feed. Clicking it opens the tab with the detail and the
 * alternatives.
 */
class NextActionRow extends JPanel
{
	NextActionRow(NextAction action, Runnable onClick)
	{
		Color accent = accent(action.getBucket());

		setLayout(new BorderLayout(4, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 1, 0, accent),
			UiUtils.padding(4, 6, 4, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);

		JLabel tag = UiUtils.muted(action.getBucket().getLabel().toUpperCase());
		tag.setForeground(accent);
		top.add(tag, BorderLayout.CENTER);

		if (action.getHours() != null)
		{
			JLabel hours = UiUtils.body(UiUtils.hours(action.getHours()));
			hours.setHorizontalAlignment(SwingConstants.RIGHT);
			top.add(hours, BorderLayout.EAST);
		}

		UiUtils.capHeight(top);
		body.add(top);

		JLabel title = new JLabel(UiUtils.ellipsise(action.getTitle(), 32));
		title.setFont(UiUtils.smallBold());
		title.setForeground(Color.WHITE);
		body.add(line(title));

		JLabel detail = UiUtils.muted(UiUtils.ellipsise(action.getDetail(), 40));
		body.add(line(detail));

		add(body, BorderLayout.CENTER);

		setToolTipText("<html>" + escape(action.getTitle()) + "<br>" + escape(action.getDetail()) + "</html>");
		setCursor(new Cursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		UiUtils.capHeight(this);
	}

	private static JPanel line(JLabel label)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(label, BorderLayout.CENTER);
		UiUtils.capHeight(row);
		return row;
	}

	private static Color accent(NextAction.Bucket bucket)
	{
		switch (bucket)
		{
			case PINNED:
				return ColorScheme.BRAND_ORANGE;
			case GOAL:
				return UiUtils.PENDING;
			case QUICK_WIN:
				return UiUtils.MET;
			default:
				return ColorScheme.MEDIUM_GRAY_COLOR;
		}
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
