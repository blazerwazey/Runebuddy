package com.runebuddy.ui;

import com.runebuddy.engine.RequirementStatus;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * A row of small labels naming what a method or item needs, green for met, red for not,
 * amber for things we can only advise on.
 */
class RequirementChip extends JPanel
{
	RequirementChip(java.util.List<RequirementStatus> statuses)
	{
		setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(UiUtils.padding(0, 0, 0, 0));

		for (RequirementStatus status : statuses)
		{
			add(chip(status));
		}
	}

	private static JLabel chip(RequirementStatus status)
	{
		JLabel label = new JLabel(UiUtils.ellipsise(status.getLabel(), 28));
		label.setFont(UiUtils.smallBold());
		label.setForeground(colorFor(status));
		label.setToolTipText(status.getLabel());
		return label;
	}

	private static Color colorFor(RequirementStatus status)
	{
		if (status.isAdvisory())
		{
			return UiUtils.PENDING;
		}

		return status.isMet() ? UiUtils.MET : UiUtils.UNMET;
	}
}
