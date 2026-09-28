package com.runebuddy.ui;

import com.runebuddy.engine.Goal;
import com.runebuddy.engine.GoalStore;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * The right-click "set as goal" menu shared by the gear and activity cards.
 */
final class GoalMenu
{
	private GoalMenu()
	{
	}

	/**
	 * Gives a card a right-click menu offering each candidate as a goal, or its removal
	 * when it is already one.
	 *
	 * @param candidates display name to goal, in menu order
	 */
	static void attach(JComponent card, PanelContext context, Map<String, Goal> candidates)
	{
		if (candidates.isEmpty())
		{
			return;
		}

		JPopupMenu menu = new JPopupMenu();
		boolean full = context.goals().isFull();

		for (Map.Entry<String, Goal> entry : candidates.entrySet())
		{
			Goal goal = entry.getValue();
			JMenuItem item;

			if (context.goals().contains(goal))
			{
				item = new JMenuItem("Remove goal: " + entry.getKey());
				item.addActionListener(e -> context.removeGoal(goal));
			}
			else if (full)
			{
				item = new JMenuItem("Goals full (" + GoalStore.MAX_GOALS + ") — remove one first");
				item.setEnabled(false);
			}
			else
			{
				item = new JMenuItem("Set as goal: " + entry.getKey());
				item.addActionListener(e -> context.addGoal(goal));
			}

			menu.add(item);
		}

		card.setComponentPopupMenu(menu);
		inheritMenu(card);
	}

	/**
	 * True when any of the candidates is already a goal.
	 */
	static boolean anySet(PanelContext context, Map<String, Goal> candidates)
	{
		for (Goal goal : candidates.values())
		{
			if (context.goals().contains(goal))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Labels and inner panels do not pass right-clicks up by default, so without this
	 * the menu only opens on the card's bare padding.
	 */
	private static void inheritMenu(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JComponent)
			{
				((JComponent) child).setInheritsPopupMenu(true);
			}

			if (child instanceof Container)
			{
				inheritMenu((Container) child);
			}
		}
	}
}
