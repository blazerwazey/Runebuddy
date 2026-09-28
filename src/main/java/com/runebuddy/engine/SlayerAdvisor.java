package com.runebuddy.engine;

import com.runebuddy.data.DataStore;
import com.runebuddy.data.SlayerTaskInfo;
import com.runebuddy.data.SlayerVerdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Turns the current Slayer task into advice. The task always comes from the game; the
 * advice comes from the data file, and a task the file does not know is still shown.
 */
public class SlayerAdvisor
{
	public static final int SKIP_COST = 30;
	public static final int BLOCK_COST = 100;

	private final DataStore data;

	public SlayerAdvisor(DataStore data)
	{
		this.data = data;
	}

	/**
	 * Advice for the player's current task, or null when they have none.
	 */
	@Nullable
	public SlayerAdvice advise(PlayerProfile profile)
	{
		SlayerTask task = profile.getSlayerTask();
		if (task == null)
		{
			return null;
		}

		SlayerTaskInfo info = data.slayerTask(task.getName());
		int points = profile.getSlayerPoints();

		List<String> locations = new ArrayList<>();
		if (task.getArea() != null)
		{
			locations.add(task.getArea() + " (Konar's pick)");
		}

		if (info == null)
		{
			return new SlayerAdvice(task, null, "No advice for this task yet", false,
				Collections.unmodifiableList(locations));
		}

		if (task.getArea() == null)
		{
			locations.addAll(info.getLocations());
		}

		String why = info.getWhy() == null ? "" : ": " + info.getWhy().substring(0, 1).toLowerCase() + info.getWhy().substring(1);
		String verdict;
		boolean skipNow = false;

		if (info.getVerdict() == SlayerVerdict.DO)
		{
			verdict = "Worth doing" + why;
		}
		else if (points >= SKIP_COST)
		{
			skipNow = true;
			verdict = info.getVerdict() == SlayerVerdict.BLOCK && points >= BLOCK_COST
				? "Block it (" + BLOCK_COST + " points)" + why
				: "Skip it (" + SKIP_COST + " of your " + points + " points)" + why;
		}
		else
		{
			verdict = info.getVerdict().getLabel() + ", but a skip needs " + SKIP_COST
				+ " points (" + points + " now)" + why;
		}

		return new SlayerAdvice(task, info, verdict, skipNow, Collections.unmodifiableList(locations));
	}
}
