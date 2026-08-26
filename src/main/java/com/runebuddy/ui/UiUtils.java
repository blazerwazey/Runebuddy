package com.runebuddy.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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

	static Color goldColor(int gpPerHour)
	{
		if (gpPerHour == 0)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}

		return gpPerHour > 0 ? MET : UNMET;
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
