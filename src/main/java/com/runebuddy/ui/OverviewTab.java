package com.runebuddy.ui;

import com.runebuddy.engine.Goal;
import com.runebuddy.engine.GoalPlan;
import com.runebuddy.engine.NextAction;
import com.runebuddy.engine.NextActions;
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
 * The "just tell me what to do" view: a snapshot of the account, one feed of what to do
 * next, and the player's goals.
 */
class OverviewTab extends JPanel
{
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

		List<Goal> goals = context.goals().goals();
		List<NextAction> feed = context.next().build(profile, NextActions.Inputs.builder()
			.goals(goals)
			.style(context.combatStyle(profile))
			.settings(context.settings())
			.contentCategories(context.contentCategories())
			.itemNames(context.itemNames(profile))
			.prices(context.prices(profile))
			.build());

		content.add(sectionHeading("Next up"));
		if (feed.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing to suggest", "Nothing in the data set fits this account yet.");
			content.add(error);
		}
		else
		{
			for (NextAction action : feed)
			{
				content.add(new NextActionRow(action, () -> context.navigate(action)));
			}
		}

		addGoals(profile, goals);

		revalidate();
		repaint();
	}

	private void addGoals(PlayerProfile profile, List<Goal> goals)
	{
		content.add(sectionHeading("Your goals"));

		if (goals.isEmpty())
		{
			JLabel hint = UiUtils.muted("<html><body style='width:170px'>Set a goal from the Skills tab,"
				+ " or right-click anything on the Gear or Do tabs.</body></html>");
			hint.setBorder(UiUtils.padding(0, 6, 4, 6));
			JPanel wrap = new JPanel(new BorderLayout());
			wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrap.add(hint, BorderLayout.CENTER);
			UiUtils.capHeight(wrap);
			content.add(wrap);
			return;
		}

		List<GoalPlan> plans = context.goalPlanner()
			.planAll(goals, profile, context.settings(), context.itemNames(profile));
		for (GoalPlan plan : plans)
		{
			content.add(new GoalCard(plan, () -> context.removeGoal(plan.getGoal())));
		}
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
