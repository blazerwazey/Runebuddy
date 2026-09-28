package com.runebuddy.ui;

import com.runebuddy.engine.Route;
import com.runebuddy.engine.RouteLeg;
import com.runebuddy.engine.RouteOption;
import com.runebuddy.engine.RouteStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * The trade-off between ways of reaching a level, side by side: how long each takes and
 * what it costs or earns. Styles that land on the same route share one row.
 */
class RouteComparison extends JPanel
{
	/**
	 * @param currentLevel   the player's level, so the target cannot go below it
	 * @param onTargetChange called on the EDT with the new target level
	 * @param isGoal         true when the target is the player's saved goal for the skill
	 * @param onToggleGoal   saves the target as a goal, or clears it when it is one
	 */
	RouteComparison(List<RouteOption> options, int currentLevel, int target, IntConsumer onTargetChange,
					boolean isGoal, Runnable onToggleGoal)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		add(header(currentLevel, target, onTargetChange));
		add(goalLink(target, isGoal, onToggleGoal));

		boolean anyLegs = false;
		for (RouteOption option : options)
		{
			anyLegs |= !option.getRoute().getLegs().isEmpty();
		}

		if (!anyLegs)
		{
			// Usually a quest or item gates every method, which "Coming up" spells out.
			JLabel none = UiUtils.muted("Nothing available yet; see below.");
			none.setForeground(UiUtils.PENDING);
			JPanel wrap = new JPanel(new BorderLayout());
			wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			wrap.setBorder(UiUtils.padding(4, 0, 0, 0));
			wrap.add(none, BorderLayout.CENTER);
			UiUtils.capHeight(wrap);
			add(wrap);
		}
		else
		{
			for (RouteOption option : options)
			{
				add(row(option));
			}
		}

		UiUtils.capHeight(this);
	}

	private static JPanel header(int currentLevel, int target, IntConsumer onTargetChange)
	{
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel title = UiUtils.title("Ways to level");
		header.add(title, BorderLayout.CENTER);

		int min = Math.min(currentLevel + 1, 99);
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(target, 99)), min, 99, 1));
		spinner.setToolTipText("Target level to plan routes to");
		spinner.addChangeListener(e ->
		{
			int chosen = (Integer) spinner.getValue();
			// The caller rebuilds the panel this spinner lives in, so let the event
			// finish before tearing it down.
			SwingUtilities.invokeLater(() -> onTargetChange.accept(chosen));
		});
		header.add(spinner, BorderLayout.EAST);

		UiUtils.capHeight(header);
		return header;
	}

	private static JPanel goalLink(int target, boolean isGoal, Runnable onToggleGoal)
	{
		JLabel link = UiUtils.body(isGoal ? "★ Your goal. Click to clear" : "Set " + target + " as a goal");
		Color normal = isGoal ? UiUtils.PENDING : ColorScheme.BRAND_ORANGE;
		link.setForeground(normal);
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onToggleGoal.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(normal);
			}
		});

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setBorder(UiUtils.padding(2, 0, 0, 0));
		wrap.add(link, BorderLayout.CENTER);
		UiUtils.capHeight(wrap);
		return wrap;
	}

	private static JPanel row(RouteOption option)
	{
		Route route = option.getRoute();

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(UiUtils.padding(5, 0, 0, 0));
		row.setToolTipText(tooltip(route));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel styles = new JLabel(UiUtils.ellipsise(styleNames(option.getStyles()), 30));
		styles.setFont(UiUtils.smallBold());
		styles.setForeground(ColorScheme.BRAND_ORANGE);
		top.add(styles, BorderLayout.CENTER);

		JLabel hours = new JLabel((route.isComplete() ? "" : "≥ ") + UiUtils.hours(route.getHours()),
			SwingConstants.RIGHT);
		hours.setFont(UiUtils.smallBold());
		hours.setForeground(Color.WHITE);
		top.add(hours, BorderLayout.EAST);
		UiUtils.capHeight(top);
		row.add(top);

		JPanel bottom = new JPanel(new BorderLayout(4, 0));
		bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		bottom.add(UiUtils.muted(UiUtils.ellipsise(methodChain(route), 34)), BorderLayout.CENTER);

		JLabel gold = new JLabel(UiUtils.goldTotal(route.getGold()), SwingConstants.RIGHT);
		gold.setFont(UiUtils.smallBold());
		gold.setForeground(UiUtils.goldColor(route.getGold()));
		bottom.add(gold, BorderLayout.EAST);
		UiUtils.capHeight(bottom);
		row.add(bottom);

		if (!route.isComplete())
		{
			JLabel blocked = UiUtils.muted("Nothing known past level " + route.getBlockedAtLevel());
			blocked.setForeground(UiUtils.PENDING);
			JPanel wrap = new JPanel(new BorderLayout());
			wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			wrap.add(blocked, BorderLayout.CENTER);
			UiUtils.capHeight(wrap);
			row.add(wrap);
		}

		UiUtils.capHeight(row);
		return row;
	}

	private static String styleNames(List<RouteStyle> styles)
	{
		List<String> names = new ArrayList<>();
		for (RouteStyle style : styles)
		{
			names.add(style.getLabel());
		}

		return String.join(" · ", names);
	}

	/**
	 * "Willows → Teaks → Redwoods", using the shortest recognisable name for each.
	 */
	private static String methodChain(Route route)
	{
		List<String> names = new ArrayList<>();
		for (RouteLeg leg : route.getLegs())
		{
			names.add(leg.getMethod().getName());
		}

		return names.isEmpty() ? "—" : String.join(" → ", names);
	}

	private static String tooltip(Route route)
	{
		StringBuilder html = new StringBuilder("<html>");
		for (RouteLeg leg : route.getLegs())
		{
			html.append(escape(leg.getMethod().getName()))
				.append(": ").append(leg.getFromLevel()).append("–").append(leg.getToLevel())
				.append(", ").append(UiUtils.hours(leg.getHours()))
				.append(", ").append(UiUtils.goldTotal(leg.getGold()))
				.append("<br>");
		}

		html.append("<i>Estimates from typical rates; yours will vary.</i></html>");
		return html.toString();
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
