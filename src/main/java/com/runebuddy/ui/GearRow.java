package com.runebuddy.ui;

import com.runebuddy.data.GearItem;
import com.runebuddy.engine.GearSuggestion;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * One equipment slot: what you have, what to buy next, and what to aim for.
 */
class GearRow extends JPanel
{
	private static final int ICON_SIZE = 32;

	GearRow(GearSuggestion suggestion, PanelContext context)
	{
		setLayout(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		// The icon shows what to aim for, since that is the thing the row is about.
		GearItem illustrate = firstNonNull(suggestion.getNext(), suggestion.getLocked(),
			suggestion.getGoal(), suggestion.getOwned());
		add(icon(illustrate, context), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		body.add(UiUtils.title(suggestion.label()));
		body.add(currentLine(suggestion));

		if (suggestion.getNext() != null)
		{
			body.add(upgradeLine("Buy next", suggestion.getNext(), context, UiUtils.MET));

			// When price held us back from the ceiling, name the ceiling too so the
			// player knows what they are saving toward.
			GearItem goal = suggestion.getGoal();
			if (goal != null && goal.getItemId() != suggestion.getNext().getItemId())
			{
				body.add(upgradeLine("Best for your level", goal, context, ColorScheme.BRAND_ORANGE));
			}
		}
		else if (suggestion.isSatisfied())
		{
			body.add(UiUtils.body("Best you can use — nothing to buy"));
		}

		if (suggestion.getLocked() != null && suggestion.getLockedReport() != null)
		{
			body.add(upgradeLine("Aim for", suggestion.getLocked(), context, UiUtils.PENDING));

			JLabel blocking = UiUtils.muted(
				UiUtils.ellipsise(suggestion.getLockedReport().blockingSummary(), 36));
			blocking.setForeground(UiUtils.UNMET);
			body.add(blocking);
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	/**
	 * What the player has right now: worn if we can see it, otherwise best owned.
	 */
	private static JLabel currentLine(GearSuggestion suggestion)
	{
		if (suggestion.getEquipped() != null)
		{
			return UiUtils.body("Wearing: " + UiUtils.ellipsise(suggestion.getEquipped().getName(), 26));
		}

		if (suggestion.getOwned() != null)
		{
			return UiUtils.body("Own: " + UiUtils.ellipsise(suggestion.getOwned().getName(), 28));
		}

		return UiUtils.muted("Nothing here yet");
	}

	private static JLabel upgradeLine(String prefix, GearItem item, PanelContext context, java.awt.Color color)
	{
		int price = context.priceOf(item.getItemId());
		String text = prefix + ": " + UiUtils.ellipsise(item.getName(), 22);
		if (price > 0)
		{
			text += "  " + UiUtils.price(price);
		}

		JLabel label = UiUtils.body(text);
		label.setForeground(color);
		label.setToolTipText(item.getNotes() == null ? item.getName() : item.getNotes());
		return label;
	}

	private static JLabel icon(@Nullable GearItem item, PanelContext context)
	{
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));

		if (item != null)
		{
			AsyncBufferedImage image = context.itemImage(item.getItemId());
			if (image != null)
			{
				// Paints itself in once the sprite has been loaded from the cache.
				image.addTo(label);
			}
		}

		return label;
	}

	@Nullable
	private static GearItem firstNonNull(GearItem... items)
	{
		for (GearItem item : items)
		{
			if (item != null)
			{
				return item;
			}
		}

		return null;
	}
}
