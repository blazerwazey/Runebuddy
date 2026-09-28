package com.runebuddy.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What a route to a level optimises for. {@link #RECOMMENDED} follows the player's own
 * weighting; the rest are single-minded extremes, shown side by side so the trade-off
 * between them is visible rather than hidden inside one blended score.
 */
@AllArgsConstructor
@Getter
public enum RouteStyle
{
	RECOMMENDED("Recommended"),
	FASTEST("Fastest"),
	CHEAPEST("Cheapest"),
	AFK("Most AFK"),
	PROFITABLE("Most profitable");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
