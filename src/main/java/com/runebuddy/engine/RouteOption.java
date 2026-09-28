package com.runebuddy.engine;

import java.util.List;
import lombok.Value;

/**
 * One distinct route, and every style that arrived at it. Styles often agree — the
 * fastest way can also be the cheapest — and saying so once is clearer than repeating
 * the same route under different names.
 */
@Value
public class RouteOption
{
	List<RouteStyle> styles;

	Route route;
}
