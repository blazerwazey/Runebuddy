package com.runebuddy.ui;

import com.runebuddy.engine.CombatAchievementProgress;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ProgressBar;

/**
 * Combat achievement points and the distance to the next reward tier.
 */
class CombatAchievementCard extends JPanel
{
	CombatAchievementCard(CombatAchievementProgress progress)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		body.add(row(UiUtils.title("Combat achievements")));

		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(1000);
		bar.setValue((int) Math.round(progress.fractionToNext() * 1000));
		bar.setLeftLabel(progress.getReached() == null ? "" : progress.getReached().getLabel());
		bar.setRightLabel(progress.getNext() == null ? "" : progress.getNext().getLabel());
		bar.setCenterLabel("");
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setForeground(progress.isNextTierClose() ? UiUtils.MET : ColorScheme.BRAND_ORANGE);
		bar.setPreferredSize(new Dimension(0, 14));

		JPanel barRow = new JPanel(new BorderLayout());
		barRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		barRow.setBorder(UiUtils.padding(3, 0, 3, 0));
		barRow.add(bar, BorderLayout.CENTER);
		UiUtils.capHeight(barRow);
		body.add(barRow);

		JLabel summary = UiUtils.body(progress.summary());
		if (progress.isNextTierClose())
		{
			summary.setForeground(UiUtils.MET);
		}
		body.add(row(summary));

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	private static JPanel row(JLabel label)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);
		UiUtils.capHeight(row);
		return row;
	}
}
