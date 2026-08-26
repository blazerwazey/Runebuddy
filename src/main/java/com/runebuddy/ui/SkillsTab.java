package com.runebuddy.ui;

import com.runebuddy.data.Skills;
import com.runebuddy.engine.MethodScore;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.SkillAdvice;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
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
			.adviceFor(selected, profile, context.settings(), context.itemNames());

		detail.add(heading(Skills.displayName(selected) + " — level " + advice.getLevel()));

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

	private static JLabel heading(String text)
	{
		JLabel heading = UiUtils.title(text);
		heading.setBorder(UiUtils.padding(8, 6, 4, 6));
		UiUtils.capHeight(heading);
		return heading;
	}
}
