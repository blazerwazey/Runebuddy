package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.TrainingMethod;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TimeEstimatorTest
{
	private static final double DELTA = 1e-9;

	/**
	 * A one-skill data set built from terse method descriptions, so each test states the
	 * exact rates it relies on.
	 */
	private static DataStore data(String... methods)
	{
		return DataStore.fromJson(new Gson(), "[" + String.join(",", methods) + "]", "[]", "[]", "[]");
	}

	private static String method(String id, int minLevel, String curve, int gpPerHour, String effort)
	{
		return "{\"id\":\"" + id + "\",\"skill\":\"FISHING\",\"name\":\"" + id + "\","
			+ "\"minLevel\":" + minLevel + ",\"xpCurve\":[" + curve + "],"
			+ "\"gpPerHour\":" + gpPerHour + ",\"effort\":\"" + effort + "\",\"members\":false}";
	}

	private static String flat(int xpPerHour)
	{
		return "{\"level\":1,\"xpPerHour\":" + xpPerHour + "}";
	}

	private static TrainingMethod only(DataStore data)
	{
		return data.getMethods().get(0);
	}

	private static PlayerProfile fishingAt(int level)
	{
		return PlayerProfile.flat(1).toBuilder().level(Skill.FISHING, level).build();
	}

	private static TimeEstimator estimator(DataStore data)
	{
		return new TimeEstimator(new RecommendationEngine(data));
	}

	@Test
	public void flatRateMatchesArithmetic()
	{
		TrainingMethod method = only(data(method("flat", 1, flat(10_000), 0, "AFK")));

		double hours = TimeEstimator.hoursToLevel(method, 0, 10);

		assertEquals(Experience.getXpForLevel(10) / 10_000d, hours, DELTA);
	}

	@Test
	public void risingRateIsFasterThanItsStartingRate()
	{
		TrainingMethod method = only(data(method("curve", 1,
			"{\"level\":1,\"xpPerHour\":10000},{\"level\":60,\"xpPerHour\":60000}", 0, "AFK")));

		double hours = TimeEstimator.hoursToLevel(method, 0, 60);
		double atStartingRate = Experience.getXpForLevel(60) / 10_000d;

		assertTrue("the rate climbs with level, so the real figure must be well under "
			+ atStartingRate + "h, got " + hours, hours < atStartingRate / 2);
	}

	@Test
	public void partWayThroughALevelCountsOnlyTheRemainder()
	{
		TrainingMethod method = only(data(method("flat", 1, flat(50_000), 0, "AFK")));
		int start = Experience.getXpForLevel(70);
		int next = Experience.getXpForLevel(71);
		int halfway = start + (next - start) / 2;

		double hours = TimeEstimator.hoursToLevel(method, halfway, 71);

		assertEquals((next - halfway) / 50_000d, hours, DELTA);
	}

	@Test
	public void alreadyThereTakesNoTime()
	{
		TrainingMethod method = only(data(method("flat", 1, flat(50_000), 0, "AFK")));

		assertEquals(0d, TimeEstimator.hoursToLevel(method, Experience.getXpForLevel(80), 70), DELTA);
	}

	@Test
	public void routeSwitchesMethodAtTheUnlockLevel()
	{
		DataStore data = data(
			method("slow", 1, flat(5_000), 0, "MEDIUM"),
			method("fast", 20, flat(20_000), 0, "MEDIUM"));

		Route route = estimator(data).route(Skill.FISHING, fishingAt(1), 40, RouteStyle.FASTEST,
			EngineSettings.defaults(), null);

		assertTrue(route.isComplete());
		assertEquals(2, route.getLegs().size());
		assertEquals("slow", route.getLegs().get(0).getMethod().getId());
		assertEquals(1, route.getLegs().get(0).getFromLevel());
		assertEquals(20, route.getLegs().get(0).getToLevel());
		assertEquals("fast", route.getLegs().get(1).getMethod().getId());
		assertEquals(40, route.getLegs().get(1).getToLevel());

		double expected = Experience.getXpForLevel(20) / 5_000d
			+ (Experience.getXpForLevel(40) - Experience.getXpForLevel(20)) / 20_000d;
		assertEquals(expected, route.getHours(), 1e-6);
	}

	@Test
	public void routeUsesTheCurrentPartialLevel()
	{
		DataStore data = data(method("flat", 1, flat(40_000), 0, "AFK"));
		int xp = Experience.getXpForLevel(50) + 50_000;
		PlayerProfile profile = fishingAt(50).toBuilder().xp(Skill.FISHING, xp).build();

		Route route = estimator(data).route(Skill.FISHING, profile, 60, RouteStyle.FASTEST,
			EngineSettings.defaults(), null);

		assertEquals((Experience.getXpForLevel(60) - xp) / 40_000d, route.getHours(), 1e-6);
	}

	@Test
	public void stylesPickTheirExtremes()
	{
		DataStore data = data(
			method("burner", 1, flat(100_000), -200_000, "HIGH"),
			method("chill", 1, flat(30_000), 0, "AFK"),
			method("earner", 1, flat(20_000), 300_000, "MEDIUM"));
		TimeEstimator estimator = estimator(data);
		PlayerProfile profile = fishingAt(50);

		assertEquals("burner", firstMethod(estimator, profile, RouteStyle.FASTEST));
		assertEquals("chill", firstMethod(estimator, profile, RouteStyle.AFK));
		assertEquals("earner", firstMethod(estimator, profile, RouteStyle.PROFITABLE));
		// Both free options cost nothing, so the faster of them is the cheapest route.
		assertEquals("chill", firstMethod(estimator, profile, RouteStyle.CHEAPEST));
	}

	@Test
	public void cheapestFallsBackToLeastGoldPerExperience()
	{
		DataStore data = data(
			method("pricey", 1, flat(100_000), -100_000, "HIGH"),
			method("thrifty", 1, flat(50_000), -20_000, "HIGH"));

		assertEquals("thrifty", firstMethod(estimator(data), fishingAt(50), RouteStyle.CHEAPEST));
	}

	@Test
	public void extremesSkipMethodsThePlayerHasOutgrown()
	{
		String chickens = method("chickens", 1, flat(5_000), 0, "AFK")
			.replace("\"minLevel\":1,", "\"minLevel\":1,\"recommendedUntil\":20,");
		DataStore data = data(chickens, method("crabs", 20, flat(40_000), -1_000, "AFK"));
		TimeEstimator estimator = estimator(data);

		// Chickens are free, but at 50 they are long outgrown, so the cheapest sensible
		// route pays a little for crabs instead.
		assertEquals("crabs", firstMethod(estimator, fishingAt(50), RouteStyle.CHEAPEST));
		// Below 20 nothing is outgrown and the free option wins.
		assertEquals("chickens", firstMethod(estimator, fishingAt(10), RouteStyle.CHEAPEST));
	}

	@Test
	public void goldIsTotalledAcrossTheRoute()
	{
		DataStore data = data(method("earner", 1, flat(50_000), 100_000, "AFK"));

		Route route = estimator(data).route(Skill.FISHING, fishingAt(60), 70, RouteStyle.FASTEST,
			EngineSettings.defaults(), null);

		assertEquals(Math.round(route.getHours() * 100_000), route.getGold());
	}

	@Test
	public void routeReportsWhereItRunsOutOfMethods()
	{
		DataStore data = data(method("late", 30, flat(20_000), 0, "AFK"));

		Route route = estimator(data).route(Skill.FISHING, fishingAt(10), 40, RouteStyle.FASTEST,
			EngineSettings.defaults(), null);

		assertEquals(Integer.valueOf(10), route.getBlockedAtLevel());
		assertTrue(route.getLegs().isEmpty());
	}

	@Test
	public void targetAtOrBelowCurrentLevelIsAnEmptyCompleteRoute()
	{
		DataStore data = data(method("flat", 1, flat(40_000), 0, "AFK"));

		Route route = estimator(data).route(Skill.FISHING, fishingAt(80), 70, RouteStyle.RECOMMENDED,
			EngineSettings.defaults(), null);

		assertTrue(route.isComplete());
		assertTrue(route.isAlreadyThere());
		assertTrue(route.getLegs().isEmpty());
		assertEquals(0d, route.getHours(), DELTA);
	}

	@Test
	public void bundledDataGivesAPlausibleRouteToNinetyNine()
	{
		TimeEstimator estimator = estimator(DataStore.load(new Gson()));
		PlayerProfile fresh = PlayerProfile.flat(1);

		Route route = estimator.route(Skill.WOODCUTTING, fresh, 99, RouteStyle.FASTEST,
			EngineSettings.defaults(), null);

		assertTrue(route.isComplete());
		assertNull(route.getBlockedAtLevel());
		assertTrue("1-99 woodcutting should take tens to hundreds of hours, got " + route.getHours(),
			route.getHours() > 50 && route.getHours() < 400);
		assertTrue("the route should switch methods as better trees unlock", route.getLegs().size() >= 3);
	}

	@Test
	public void alternativesMergeStylesThatAgree()
	{
		DataStore data = data(method("only", 1, flat(30_000), 0, "AFK"));

		List<RouteOption> options = estimator(data).alternatives(Skill.FISHING, fishingAt(50), 60,
			EngineSettings.defaults(), null);

		assertEquals("one method means one route, whatever the style", 1, options.size());
		assertEquals(Arrays.asList(RouteStyle.values()), options.get(0).getStyles());
	}

	@Test
	public void alternativesKeepDistinctRoutesApart()
	{
		DataStore data = data(
			method("burner", 1, flat(100_000), -200_000, "HIGH"),
			method("chill", 1, flat(30_000), 0, "AFK"),
			method("earner", 1, flat(20_000), 300_000, "MEDIUM"));

		List<RouteOption> options = estimator(data).alternatives(Skill.FISHING, fishingAt(50), 60,
			EngineSettings.defaults(), null);

		assertEquals(3, options.size());
		int styles = 0;
		for (RouteOption option : options)
		{
			styles += option.getStyles().size();
		}
		assertEquals("every style is accounted for exactly once", RouteStyle.values().length, styles);
	}

	private static String firstMethod(TimeEstimator estimator, PlayerProfile profile, RouteStyle style)
	{
		return estimator.route(Skill.FISHING, profile, 60, style, EngineSettings.defaults(), null)
			.getLegs().get(0).getMethod().getId();
	}
}
