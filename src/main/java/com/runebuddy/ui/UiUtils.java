package com.runebuddy.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Small shared bits of panel styling, kept in one place so the three tabs look like the
 * same plugin.
 */
final class UiUtils
{
	/**
	 * Green used for requirements the player meets and for profit.
	 */
	static final Color MET = new Color(87, 175, 87);

	/**
	 * Red used for requirements the player fails and for costs.
	 */
	static final Color UNMET = new Color(190, 85, 85);

	/**
	 * Amber for things that are close, or that we can only advise on.
	 */
	static final Color PENDING = new Color(200, 160, 60);

	private UiUtils()
	{
	}

	static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	static JLabel body(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	static JLabel muted(String text)
	{
		JLabel label = body(text);
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		return label;
	}

	static Border padding(int top, int left, int bottom, int right)
	{
		return new EmptyBorder(top, left, bottom, right);
	}

	/**
	 * Stops a component stretching vertically when it is dropped into a BoxLayout.
	 */
	static void capHeight(Component component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
	}

	/**
	 * "42k xp/hr".
	 */
	static String xpRate(int xpPerHour)
	{
		return QuantityFormatter.quantityToRSDecimalStack(xpPerHour) + " xp/hr";
	}

	/**
	 * "+180k/hr" for profit, "-120k/hr" for a cost, "free" for neither.
	 */
	static String goldRate(int gpPerHour)
	{
		if (gpPerHour == 0)
		{
			return "free";
		}

		String amount = QuantityFormatter.quantityToRSDecimalStack(Math.abs(gpPerHour));
		return (gpPerHour > 0 ? "+" : "-") + amount + "/hr";
	}

	/**
	 * "40 min", "4.5h" or "120h": precise where it helps, rounded where false precision
	 * would mislead, since every figure is an estimate.
	 */
	static String hours(double hours)
	{
		if (Double.isInfinite(hours) || Double.isNaN(hours))
		{
			return "?";
		}

		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + " min";
		}

		if (hours < 10)
		{
			return String.format(Locale.ROOT, "%.1fh", hours);
		}

		return Math.round(hours) + "h";
	}

	/**
	 * "+28.4m" for profit, "-6.4m" for a cost, "free" for neither.
	 */
	static String goldTotal(long gold)
	{
		if (gold == 0)
		{
			return "free";
		}

		return (gold > 0 ? "+" : "-") + shortAmount(Math.abs(gold));
	}

	static Color goldColor(long gold)
	{
		if (gold == 0)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}

		return gold > 0 ? MET : UNMET;
	}

	/**
	 * "950", "12.5k", "3.2m", "1.1b".
	 */
	static String shortAmount(long amount)
	{
		if (amount < 1_000)
		{
			return String.valueOf(amount);
		}

		if (amount < 1_000_000)
		{
			return trimmed(amount / 1_000d) + "k";
		}

		if (amount < 1_000_000_000)
		{
			return trimmed(amount / 1_000_000d) + "m";
		}

		return trimmed(amount / 1_000_000_000d) + "b";
	}

	private static String trimmed(double value)
	{
		return value >= 100 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value).replace(".0", "");
	}

	/**
	 * "1.2m gp", or a dash when the price is unknown.
	 */
	static String price(int gp)
	{
		return gp <= 0 ? "-" : QuantityFormatter.quantityToRSDecimalStack(gp) + " gp";
	}

	/**
	 * Truncates to fit the narrow side panel, since long method names otherwise force a
	 * horizontal scrollbar.
	 */
	static String ellipsise(String text, int maxChars)
	{
		if (text == null)
		{
			return "";
		}

		return text.length() <= maxChars ? text : text.substring(0, Math.max(0, maxChars - 1)) + "…";
	}

	static Font smallBold()
	{
		return FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
	}
}
