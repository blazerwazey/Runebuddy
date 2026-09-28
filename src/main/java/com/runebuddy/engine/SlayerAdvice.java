package com.runebuddy.engine;

import com.runebuddy.data.SlayerTaskInfo;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What to do about the current Slayer task.
 */
@Value
public class SlayerAdvice
{
	SlayerTask task;

	/**
	 * Advice from the data file, or null for a task it does not cover yet.
	 */
	@Nullable
	SlayerTaskInfo info;

	/**
	 * The verdict for this account, with its reason: "Worth doing: whip drops".
	 */
	String verdict;

	/**
	 * True when the advice is to skip and the player has the points to do it.
	 */
	boolean skipNow;

	/**
	 * Where to go, best first. Konar's assigned area leads when there is one.
	 */
	List<String> locations;
}
