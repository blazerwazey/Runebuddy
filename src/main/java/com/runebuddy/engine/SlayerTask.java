package com.runebuddy.engine;

import javax.annotation.Nullable;
import lombok.Value;

/**
 * The player's current Slayer assignment, as the game reports it.
 */
@Value
public class SlayerTask
{
	/**
	 * The task name from the game's own tables: "Abyssal demons", or a boss name for a
	 * boss task.
	 */
	String name;

	int remaining;

	/**
	 * How many were assigned, or 0 when unknown.
	 */
	int assigned;

	/**
	 * Where Konar sent the player, or null for any other master.
	 */
	@Nullable
	String area;
}
