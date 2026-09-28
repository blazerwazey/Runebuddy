package com.runebuddy.ui;

import com.runebuddy.data.Skills;
import com.runebuddy.engine.Goal;
import com.runebuddy.engine.MethodScore;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RouteOption;
import com.runebuddy.engine.SkillAdvice;
import com.runebuddy.engine.SlayerAdvice;
import com.runebuddy.engine.TimeEstimator;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * A grid of skill icons, and the ranked methods for whichever one is selected.
 */
class SkillsTab extends JPanel
{
	private static final int COLUMNS = 6;
	private static final int ICON_CELL = 28;

	private final PanelContext context;
	private final JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 2, 2));
	private final JPanel detail = new JPanel();

	/**
	 * How far ahead to plan routes when the player has not said. Ten levels is far
	 * enough for a route to switch methods, and near enough to feel reachable.
	 */
	private static final int DEFAULT_LOOKAHEAD = 10;

	/**
	 * Target level per skill as picked on the route spinner, for this session.
	 */
	private final Map<Skill, Integer> targets = new EnumMap<>(Skill.class);

	private Skill selected = Skill.ATTACK;
	private PlayerProfile profile = PlayerProfile.LOGGED_OUT;

	SkillsTab(PanelContext context)
	{
		this.context = context;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setBorder(UiUtils.padding(6, 6, 6, 6));

		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		detail.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel north = new JPanel(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(grid, BorderLayout.NORTH);
		north.add(detail, BorderLayout.CENTER);

		add(north, BorderLayout.NORTH);
		buildGrid();
	}

	/**
	 * Opens a skill's page, as if its icon had been clicked.
	 */
	void select(Skill skill)
	{
		selected = skill;
		buildGrid();
		buildDetail();
	}

	void update(PlayerProfile profile)
	{
		this.profile = profile;
		buildGrid();
		buildDetail();
	}

	private void buildGrid()
	{
		grid.removeAll();
		for (Skill skill : Skills.trainable())
		{
			grid.add(skillButton(skill));
		}

		grid.revalidate();
		grid.repaint();
	}

	private JPanel skillButton(Skill skill)
	{
		JPanel cell = new JPanel(new BorderLayout());
		cell.setPreferredSize(new Dimension(ICON_CELL, ICON_CELL));
		cell.setBackground(skill == selected
			? ColorScheme.BRAND_ORANGE_TRANSPARENT
			: ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(BorderFactory.createLineBorder(
			skill == selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR));
		cell.setToolTipText(Skills.displayName(skill)
			+ (profile.isLoggedIn() ? " — level " + profile.level(skill) : ""));
		cell.setCursor(new Cursor(Cursor.HAND_CURSOR));

		BufferedImage icon = context.skillIcon(skill);
		JLabel label = icon != null
			? new JLabel(new ImageIcon(icon), SwingConstants.CENTER)
			: new JLabel(Skills.displayName(skill).substring(0, 2), SwingConstants.CENTER);
		label.setFont(UiUtils.smallBold());
		cell.add(label, BorderLayout.CENTER);

		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				selected = skill;
				buildGrid();
				buildDetail();
			}
		});

		return cell;
	}

	private void buildDetail()
	{
		detail.removeAll();

		if (!profile.isLoggedIn())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Runebuddy", "Log in to see what to train and how.");
			detail.add(error);
			detail.revalidate();
			detail.repaint();
			return;
		}

		SkillAdvice advice = context.engine()
			.adviceFor(selected, profile, context.settings(), context.itemNames(profile));

		detail.add(heading(Skills.displayName(selected) + " — level " + advice.getLevel()));

		if (selected == Skill.SLAYER)
		{
			SlayerAdvice slayer = context.slayer().advise(profile);
			if (slayer != null)
			{
				detail.add(new SlayerTaskCard(slayer));
			}
		}

		if (advice.getLevel() < TimeEstimator.MAX_LEVEL)
		{
			detail.add(routes(advice.getLevel()));
		}

		if (advice.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing here yet",
				"Runebuddy has no methods for " + Skills.displayName(selected)
					+ " that suit this account.");
			detail.add(error);
		}
		else
		{
			for (MethodScore score : advice.getRecommended())
			{
				detail.add(new MethodCard(score, false));
			}

			List<MethodScore> soon = advice.getUnlockingSoon();
			if (!soon.isEmpty())
			{
				detail.add(heading("Coming up"));
				for (MethodScore score : soon)
				{
					detail.add(new MethodCard(score, false));
				}
			}
		}

		detail.revalidate();
		detail.repaint();
	}

	private RouteComparison routes(int level)
	{
		int target = targetFor(selected, level);
		List<RouteOption> options = context.estimator().alternatives(
			selected, profile, target, context.settings(), context.itemNames(profile));

		Integer goalLevel = context.goals().skillTarget(selected);
		boolean isGoal = goalLevel != null && goalLevel == target;
		Skill skill = selected;

		return new RouteComparison(options, level, target, chosen ->
		{
			targets.put(skill, chosen);
			buildDetail();
		}, isGoal, () ->
		{
			if (isGoal)
			{
				context.removeGoal(Goal.skill(skill, target));
			}
			else
			{
				context.addGoal(Goal.skill(skill, target));
			}
		});
	}

	/**
	 * The level to plan to: whatever the spinner was set to this session, else the
	 * player's goal for the skill, else a few levels on.
	 */
	private int targetFor(Skill skill, int level)
	{
		Integer chosen = targets.get(skill);
		if (chosen != null && chosen > level)
		{
			return chosen;
		}

		Integer goal = context.goals().skillTarget(skill);
		if (goal != null && goal > level)
		{
			return goal;
		}

		return Math.min(level + DEFAULT_LOOKAHEAD, TimeEstimator.MAX_LEVEL);
	}

	private static JLabel heading(String text)
	{
		JLabel heading = UiUtils.title(text);
		heading.setBorder(UiUtils.padding(8, 6, 4, 6));
		UiUtils.capHeight(heading);
		return heading;
	}
}
