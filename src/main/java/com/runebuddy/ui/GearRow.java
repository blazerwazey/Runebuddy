package com.runebuddy.ui;

import com.runebuddy.data.GearItem;
import com.runebuddy.engine.GearSuggestion;
import com.runebuddy.engine.PlayerProfile;
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

	GearRow(GearSuggestion suggestion, PanelContext context, PlayerProfile profile)
	{
		boolean ironman = profile.isIronman();
		setLayout(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		// The icon shows what to aim for, since that is the thing the row is about, and
		// falls back to whatever they are actually wearing.
		GearItem illustrate = firstNonNull(suggestion.getNext(), suggestion.getLocked(),
			suggestion.getGoal(), suggestion.getOwned());
		Integer iconId = illustrate != null ? illustrate.getItemId() : suggestion.getBestOwnedItemId();
		add(icon(iconId, context), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		body.add(UiUtils.title(suggestion.label()));
		body.add(currentLine(suggestion));

		if (suggestion.getNext() != null)
		{
			body.add(upgradeLine("Next upgrade", suggestion.getNext(), context, profile, UiUtils.MET, ironman));
			addSourceLine(body, suggestion.getNext(), ironman);

			// When price held us back from the ceiling, name the ceiling too so the
			// player knows what they are saving toward. Never happens on an ironman,
			// where price is not what holds anything back.
			GearItem goal = suggestion.getGoal();
			if (goal != null && goal.getItemId() != suggestion.getNext().getItemId())
			{
				body.add(upgradeLine("Best for your level", goal, context, profile, ColorScheme.BRAND_ORANGE, ironman));
			}
		}
		else if (suggestion.isSatisfied() || suggestion.getBestOwnedName() != null)
		{
			body.add(UiUtils.body("Best you can use"));
		}

		if (suggestion.getLocked() != null && suggestion.getLockedReport() != null)
		{
			body.add(upgradeLine("Aim for", suggestion.getLocked(), context, profile, UiUtils.PENDING, ironman));

			JLabel blocking = UiUtils.muted(
				UiUtils.ellipsise(suggestion.getLockedReport().blockingSummary(), 36));
			blocking.setForeground(UiUtils.UNMET);
			body.add(blocking);
			addSourceLine(body, suggestion.getLocked(), ironman);
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	/**
	 * What the player has right now: worn if we can see it, otherwise best owned.
	 */
	private static JLabel currentLine(GearSuggestion suggestion)
	{
		// The stat-derived answer comes first: it knows about items the ladder does not,
		// which is the whole point of reading the client's own equipment data.
		if (suggestion.getBestOwnedName() != null)
		{
			return UiUtils.body("Own: " + UiUtils.ellipsise(suggestion.getBestOwnedName(), 28));
		}

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

	/**
	 * Where an item comes from. Always worth saying on an ironman, who has to go and get
	 * it; on a main only worth saying when they cannot simply buy it.
	 */
	private static void addSourceLine(JPanel body, GearItem item, boolean ironman)
	{
		if (item.getSource() == null || (!ironman && item.isTradeable()))
		{
			return;
		}

		JLabel source = UiUtils.muted("From: " + UiUtils.ellipsise(item.getSource(), 32));
		source.setToolTipText(item.getSource());
		body.add(source);
	}

	private static JLabel upgradeLine(String prefix, GearItem item, PanelContext context,
									  PlayerProfile profile, java.awt.Color color, boolean ironman)
	{
		String text = prefix + ": " + UiUtils.ellipsise(item.getName(), 22);

		// A price is only information if the reader can act on it: the item has to be
		// buyable, and the account has to be one that can buy.
		if (item.isTradeable() && !ironman)
		{
			int price = context.priceOf(profile, item.getItemId());
			if (price > 0)
			{
				text += "  " + UiUtils.price(price);
			}
		}

		JLabel label = UiUtils.body(text);
		label.setForeground(color);
		label.setToolTipText(item.getNotes() == null ? item.getName() : item.getNotes());
		return label;
	}

	private static JLabel icon(@Nullable Integer itemId, PanelContext context)
	{
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));

		if (itemId != null)
		{
			AsyncBufferedImage image = context.itemImage(itemId);
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
