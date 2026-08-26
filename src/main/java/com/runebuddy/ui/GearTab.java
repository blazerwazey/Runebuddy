package com.runebuddy.ui;

import com.runebuddy.StylePreference;
import com.runebuddy.data.GearCategory;
import com.runebuddy.engine.GearAdvisor;
import com.runebuddy.engine.GearSuggestion;
import com.runebuddy.engine.PlayerProfile;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * Gear progression, one ladder at a time.
 *
 * <p>The style selector has a Tools entry alongside the combat ladders, since "which
 * pickaxe should I be using" is the same question in a different slot.
 */
class GearTab extends JPanel
{
	/**
	 * Null stands for the tool ladders, which are not a combat category.
	 */
	private static final GearCategory TOOLS = null;

	private final PanelContext context;
	private final JPanel selector = new JPanel(new GridLayout(1, 4, 2, 0));
	private final JPanel rows = new JPanel();

	private PlayerProfile profile = PlayerProfile.LOGGED_OUT;

	/**
	 * Null until the player picks one, at which point it stops following the config.
	 */
	@Nullable
	private GearCategory chosen;

	private boolean userChose;

	GearTab(PanelContext context)
	{
		this.context = context;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		selector.setBackground(ColorScheme.DARK_GRAY_COLOR);
		selector.setBorder(UiUtils.padding(6, 6, 6, 6));

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel north = new JPanel(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(selector, BorderLayout.NORTH);
		north.add(rows, BorderLayout.CENTER);

		add(north, BorderLayout.NORTH);
	}

	void update(PlayerProfile profile)
	{
		this.profile = profile;

		// A pinned Tools view has to give way if the user later hides the tool ladders.
		if (!userChose || (chosen == TOOLS && !context.config().showSkillingTools()))
		{
			chosen = defaultCategory(profile);
			userChose = false;
		}

		buildSelector();
		buildRows();
	}

	/**
	 * The config pins a style, or we infer one from the levels the account has invested.
	 */
	private GearCategory defaultCategory(PlayerProfile profile)
	{
		StylePreference preference = context.config().preferredStyle();
		if (preference.getCategory() != null)
		{
			return preference.getCategory();
		}

		return profile.isLoggedIn() ? GearAdvisor.detectStyle(profile) : GearCategory.MELEE;
	}

	private void buildSelector()
	{
		selector.removeAll();

		List<GearCategory> options = new ArrayList<>();
		options.add(GearCategory.MELEE);
		options.add(GearCategory.RANGED);
		options.add(GearCategory.MAGIC);

		for (GearCategory category : options)
		{
			selector.add(option(category.getLabel(), category));
		}

		if (context.config().showSkillingTools())
		{
			selector.add(option("Tools", TOOLS));
		}

		selector.revalidate();
		selector.repaint();
	}

	private JLabel option(String text, @Nullable GearCategory category)
	{
		boolean active = category == chosen;

		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(UiUtils.smallBold());
		label.setOpaque(true);
		label.setBackground(active ? ColorScheme.BRAND_ORANGE_TRANSPARENT : ColorScheme.DARKER_GRAY_COLOR);
		label.setForeground(active ? java.awt.Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(active ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR),
			UiUtils.padding(4, 2, 4, 2)));
		label.setCursor(new Cursor(Cursor.HAND_CURSOR));

		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				chosen = category;
				userChose = true;
				buildSelector();
				buildRows();
			}
		});

		return label;
	}

	private void buildRows()
	{
		rows.removeAll();

		if (!profile.isLoggedIn())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Runebuddy", "Log in to see what gear to aim for.");
			rows.add(error);
			rows.revalidate();
			rows.repaint();
			return;
		}

		List<GearSuggestion> suggestions = chosen == TOOLS
			? context.gear().adviseTools(profile, context.itemNames(), context.prices())
			: context.gear().adviseCombat(chosen, profile, context.itemNames(), context.prices());

		if (suggestions.isEmpty())
		{
			PluginErrorPanel error = new PluginErrorPanel();
			error.setContent("Nothing here yet", "Runebuddy has no gear data for this ladder.");
			rows.add(error);
		}
		else
		{
			if (!profile.isBankKnown())
			{
				JLabel hint = UiUtils.muted(
					"<html><body style='width:150px'>Open your bank once and Runebuddy "
						+ "will know what you already own.</body></html>");
				hint.setBorder(UiUtils.padding(0, 6, 6, 6));
				rows.add(hint);
			}

			for (GearSuggestion suggestion : suggestions)
			{
				rows.add(new GearRow(suggestion, context, profile.isIronman()));
			}
		}

		rows.revalidate();
		rows.repaint();
	}
}
