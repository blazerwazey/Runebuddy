package com.runebuddy.ui;

import com.runebuddy.data.Skills;
import com.runebuddy.data.TrainingMethod;
import com.runebuddy.engine.MethodScore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * One training method, as a card: what it is, what it gives you, what it asks of you,
 * and why it was suggested.
 */
class MethodCard extends JPanel
{
	/**
	 * @param showSkill true on the overview, where cards from different skills are mixed
	 *                  together and the skill name is the important part
	 */
	MethodCard(MethodScore score, boolean showSkill)
	{
		TrainingMethod method = score.getMethod();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		String heading = showSkill
			? Skills.displayName(method.getSkill()) + ": " + method.getName()
			: method.getName();

		JLabel name = UiUtils.title(UiUtils.ellipsise(heading, 32));
		name.setToolTipText(heading);
		body.add(name);

		body.add(rates(score));

		JLabel why = UiUtils.body(UiUtils.ellipsise(score.getRationale(), 40));
		why.setToolTipText(score.getRationale());
		why.setForeground(score.isOutgrown() ? UiUtils.PENDING : ColorScheme.LIGHT_GRAY_COLOR);
		body.add(why);

		if (!score.getRequirements().getStatuses().isEmpty())
		{
			body.add(new RequirementChip(score.getRequirements().getStatuses()));
		}

		if (method.getLocation() != null)
		{
			body.add(UiUtils.muted(UiUtils.ellipsise(method.getLocation(), 40)));
		}

		if (method.getNotes() != null)
		{
			JLabel notes = UiUtils.muted("<html><body style='width:150px'>" + escape(method.getNotes()) + "</body></html>");
			body.add(notes);
		}

		if (method.getWikiUrl() != null)
		{
			body.add(wikiLink(method.getWikiUrl()));
		}

		add(body, BorderLayout.CENTER);
		UiUtils.capHeight(this);
	}

	/**
	 * The two numbers that matter, side by side.
	 */
	private static JPanel rates(MethodScore score)
	{
		JPanel rates = new JPanel(new GridLayout(1, 2, 4, 0));
		rates.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel xp = new JLabel(UiUtils.xpRate(score.getXpPerHour()));
		xp.setFont(UiUtils.smallBold());
		xp.setForeground(ColorScheme.BRAND_ORANGE);
		rates.add(xp);

		JLabel gold = new JLabel(UiUtils.goldRate(score.getGpPerHour()), SwingConstants.RIGHT);
		gold.setFont(UiUtils.smallBold());
		gold.setForeground(UiUtils.goldColor(score.getGpPerHour()));
		gold.setToolTipText(score.getMethod().getEffort().getLabel());
		rates.add(gold);

		UiUtils.capHeight(rates);
		return rates;
	}

	private static JLabel wikiLink(String url)
	{
		JLabel link = UiUtils.body("Read the wiki guide");
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(ColorScheme.BRAND_ORANGE);
			}
		});

		return link;
	}

	/**
	 * Notes are rendered as HTML so they wrap in the narrow panel, so any markup in the
	 * data file has to be neutralised first.
	 */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
