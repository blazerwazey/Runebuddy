package com.runebuddy.ui;

import com.runebuddy.engine.Goal;
import com.runebuddy.engine.GoalPlan;
import com.runebuddy.engine.GoalStep;
import com.runebuddy.engine.RouteLeg;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ProgressBar;

/**
 * One goal: how far along it is, how long is left, and what to do next.
 */
class GoalCard extends JPanel
{
	/**
	 * Enough to see the shape of a goal without the card taking over the tab.
	 */
	private static final int MAX_STEPS = 4;

	GoalCard(GoalPlan plan, Runnable onRemove)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		body.add(titleRow(plan, onRemove));

		if (plan.getProgress() != null)
		{
			body.add(progress(plan.getProgress()));
		}

		body.add(line(summary(plan), summaryColor(plan)));

		List<GoalStep> steps = plan.getSteps();
		GoalStep next = plan.nextStep();
		int shown = 0;
		for (GoalStep step : steps)
		{
			if (shown == MAX_STEPS)
			{
				body.add(line("+" + (steps.size() - shown) + " more", ColorScheme.MEDIUM_GRAY_COLOR));
				break;
			}

			body.add(stepLine(step, step == next));
			shown++;
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	private static JPanel titleRow(GoalPlan plan, Runnable onRemove)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel title = UiUtils.title(UiUtils.ellipsise(plan.getTitle(), 28));
		title.setToolTipText(plan.getTitle());
		row.add(title, BorderLayout.CENTER);

		JLabel remove = new JLabel("✕", SwingConstants.RIGHT);
		remove.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		remove.setToolTipText("Remove this goal");
		remove.setCursor(new Cursor(Cursor.HAND_CURSOR));
		remove.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onRemove.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				remove.setForeground(UiUtils.UNMET);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				remove.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
		});
		row.add(remove, BorderLayout.EAST);

		UiUtils.capHeight(row);
		return row;
	}

	private static JPanel progress(double fraction)
	{
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(1000);
		bar.setValue((int) Math.round(fraction * 1000));
		bar.setCenterLabel(Math.round(fraction * 100) + "%");
		bar.setLeftLabel("");
		bar.setRightLabel("");
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setForeground(fraction >= 1 ? UiUtils.MET : ColorScheme.BRAND_ORANGE);
		bar.setPreferredSize(new Dimension(0, 14));

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setBorder(UiUtils.padding(3, 0, 3, 0));
		wrap.add(bar, BorderLayout.CENTER);
		UiUtils.capHeight(wrap);
		return wrap;
	}

	/**
	 * The headline: done, or how much is left and how much of that can be timed.
	 */
	static String summary(GoalPlan plan)
	{
		if (!plan.isRecognised())
		{
			return "Runebuddy no longer has data for this";
		}

		if (plan.isComplete())
		{
			return plan.getGoal().getType() == Goal.Type.CONTENT
				? "Ready to go"
				: "Done";
		}

		StringBuilder text = new StringBuilder();
		if (plan.getHours() > 0)
		{
			text.append("About ").append(UiUtils.hours(plan.getHours())).append(" of training");
		}

		if (plan.getUntimedSteps() > 0)
		{
			int untimed = plan.getUntimedSteps();
			text.append(text.length() == 0
				? untimed + (untimed == 1 ? " step, not timed" : " steps, not timed")
				: " + " + untimed + (untimed == 1 ? " more step" : " more steps"));
		}

		return text.length() == 0 ? "Almost there" : text.toString();
	}

	private static Color summaryColor(GoalPlan plan)
	{
		if (!plan.isRecognised())
		{
			return UiUtils.PENDING;
		}

		return plan.isComplete() ? UiUtils.MET : ColorScheme.LIGHT_GRAY_COLOR;
	}

	private static JPanel stepLine(GoalStep step, boolean next)
	{
		String hours = step.getHours() == null ? "" : UiUtils.hours(step.getHours());

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		String prefix = next ? "▶ " : "• ";
		JLabel label = UiUtils.body(UiUtils.ellipsise(prefix + step.getLabel(), hours.isEmpty() ? 40 : 32));
		label.setForeground(next ? Color.WHITE
			: step.getKind() == GoalStep.Kind.NOTE ? ColorScheme.MEDIUM_GRAY_COLOR
			: ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);

		if (!hours.isEmpty())
		{
			JLabel time = UiUtils.body(hours);
			time.setHorizontalAlignment(SwingConstants.RIGHT);
			row.add(time, BorderLayout.EAST);
		}

		row.setToolTipText(tooltip(step));
		UiUtils.capHeight(row);
		return row;
	}

	private static String tooltip(GoalStep step)
	{
		StringBuilder html = new StringBuilder("<html>").append(escape(step.getLabel()));
		if (step.getRoute() != null)
		{
			for (RouteLeg leg : step.getRoute().getLegs())
			{
				html.append("<br>").append(escape(leg.getMethod().getName()))
					.append(": ").append(leg.getFromLevel()).append("–").append(leg.getToLevel())
					.append(", ").append(UiUtils.hours(leg.getHours()));
			}

			if (!step.getRoute().isComplete())
			{
				html.append("<br><i>No method known past level ")
					.append(step.getRoute().getBlockedAtLevel()).append("</i>");
			}
		}

		return html.append("</html>").toString();
	}

	private static JPanel line(String text, Color color)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel label = UiUtils.body(UiUtils.ellipsise(text, 40));
		label.setToolTipText(text);
		label.setForeground(color);
		row.add(label, BorderLayout.CENTER);
		UiUtils.capHeight(row);
		return row;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
