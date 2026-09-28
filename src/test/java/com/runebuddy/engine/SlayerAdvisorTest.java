package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SlayerAdvisorTest
{
	private SlayerAdvisor advisor;

	@Before
	public void setUp()
	{
		advisor = new SlayerAdvisor(DataStore.load(new Gson()));
	}

	private static PlayerProfile onTask(String name, int points, String area)
	{
		return PlayerProfile.flat(80).toBuilder()
			.slayerTask(new SlayerTask(name, 120, 180, area))
			.slayerPoints(points)
			.build();
	}

	@Test
	public void noTaskMeansNoAdvice()
	{
		assertNull(advisor.advise(PlayerProfile.flat(80)));
	}

	@Test
	public void taskNamesMatchIgnoringCase()
	{
		SlayerAdvice advice = advisor.advise(onTask("ABYSSAL DEMONS", 0, null));

		assertNotNull(advice.getInfo());
		assertTrue(advice.getVerdict().startsWith("Worth doing"));
		assertEquals("Catacombs of Kourend", advice.getLocations().get(0));
	}

	@Test
	public void unknownTaskIsStillShownWithoutAdvice()
	{
		SlayerAdvice advice = advisor.advise(onTask("Brand new monsters", 500, null));

		assertNotNull("the task itself always shows", advice);
		assertNull(advice.getInfo());
		assertEquals("Brand new monsters", advice.getTask().getName());
		assertFalse(advice.isSkipNow());
	}

	@Test
	public void skipIsOnlyAdvisedWithEnoughPoints()
	{
		SlayerAdvice poor = advisor.advise(onTask("Waterfiends", 10, null));
		SlayerAdvice rich = advisor.advise(onTask("Waterfiends", 45, null));

		assertFalse(poor.isSkipNow());
		assertTrue(poor.getVerdict().contains("needs 30 points (10 now)"));
		assertTrue(rich.isSkipNow());
		assertTrue(rich.getVerdict().startsWith("Skip it"));
	}

	@Test
	public void blockNeedsAHundredPointsElseSkip()
	{
		assertTrue(advisor.advise(onTask("Spiritual creatures", 60, null)).getVerdict().startsWith("Skip it"));
		assertTrue(advisor.advise(onTask("Spiritual creatures", 150, null)).getVerdict().startsWith("Block it"));
	}

	@Test
	public void konarsAreaLeads()
	{
		SlayerAdvice advice = advisor.advise(onTask("Abyssal demons", 0, "the Abyss"));

		assertEquals("the Abyss (Konar's pick)", advice.getLocations().get(0));
		assertEquals("Konar decides where, so the usual spots do not apply", 1, advice.getLocations().size());
	}

	@Test
	public void everySlayerEntryLinksToTheWiki()
	{
		for (com.runebuddy.data.SlayerTaskInfo info : DataStore.load(new Gson()).getSlayerTasks())
		{
			assertTrue(info.getTask(), info.getWikiUrl().startsWith("https://oldschool.runescape.wiki/w/"));
		}
	}
}
