package com.runebuddy.data;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DataStoreTest
{
	private DataStore store;

	@Before
	public void setUp()
	{
		store = DataStore.load(new Gson());
	}

	@Test
	public void bundledDataLoadsWithoutWarnings()
	{
		assertFalse("no training methods loaded", store.getMethods().isEmpty());
		assertFalse("no gear loaded", store.getGear().isEmpty());
		assertEquals("data warnings: " + store.getWarnings(), 0, store.getWarnings().size());
	}

	@Test
	public void methodIdsAreUnique()
	{
		Set<String> ids = new HashSet<>();
		for (TrainingMethod method : store.getMethods())
		{
			assertTrue("duplicate id " + method.getId(), ids.add(method.getId()));
		}
	}

	@Test
	public void everyMethodResolvesItsEnums()
	{
		for (TrainingMethod method : store.getMethods())
		{
			assertNotNull(method.getId() + " has no skill", method.getSkill());
			assertNotNull(method.getId() + " has no effort", method.getEffort());
			assertFalse(method.getId() + " has an empty curve", method.getXpCurve().isEmpty());
		}
	}

	@Test
	public void everyGearItemResolvesItsEnums()
	{
		for (GearItem item : store.getGear())
		{
			assertNotNull(item.getName() + " has no slot", item.getSlot());
			assertNotNull(item.getName() + " has no category", item.getCategory());
			assertTrue(item.getName() + " has no item id", item.getItemId() > 0);
		}
	}

	@Test
	public void ladderIsOrderedByTier()
	{
		List<GearItem> weapons = store.ladder(GearCategory.MELEE, EquipSlot.WEAPON);
		assertFalse("no melee weapons in the data", weapons.isEmpty());

		int previous = Integer.MIN_VALUE;
		for (GearItem item : weapons)
		{
			assertTrue("melee weapon ladder is out of order at " + item.getName(), item.getTier() > previous);
			previous = item.getTier();
		}
	}

	@Test
	public void toolsAreIndexedBySkill()
	{
		List<GearItem> pickaxes = store.toolsFor(Skill.MINING);
		assertFalse("no mining tools in the data", pickaxes.isEmpty());

		for (GearItem pickaxe : pickaxes)
		{
			assertEquals(GearCategory.SKILLING, pickaxe.getCategory());
			assertEquals(Skill.MINING, pickaxe.getToolFor());
		}
	}

	@Test
	public void methodsAreIndexedBySkill()
	{
		List<TrainingMethod> mining = store.methodsFor(Skill.MINING);
		assertFalse("no mining methods in the data", mining.isEmpty());

		for (TrainingMethod method : mining)
		{
			assertEquals(Skill.MINING, method.getSkill());
		}
	}

	@Test
	public void xpCurveInterpolatesBetweenBreakpoints()
	{
		TrainingMethod iron = store.getMethods().stream()
			.filter(m -> "mining_iron_powermine".equals(m.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("seed method missing"));

		// Curve is 15 -> 20000, 60 -> 42000, 99 -> 52000.
		assertEquals(20_000, iron.xpAt(15));
		assertEquals(42_000, iron.xpAt(60));
		assertEquals(52_000, iron.xpAt(99));

		// Below and above the curve clamps rather than extrapolating.
		assertEquals(20_000, iron.xpAt(1));

		int midway = iron.xpAt(37);
		assertTrue("interpolation should land between the breakpoints",
			midway > 20_000 && midway < 42_000);
	}

	@Test
	public void outgrownReflectsRecommendedUntil()
	{
		TrainingMethod copper = store.getMethods().stream()
			.filter(m -> "mining_copper_tin".equals(m.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("seed method missing"));

		assertFalse(copper.isOutgrown(15));
		assertTrue(copper.isOutgrown(16));
	}

	@Test
	public void questRequirementsResolveToTheApiEnum()
	{
		TrainingMethod nmz = store.getMethods().stream()
			.filter(m -> "attack_nightmare_zone".equals(m.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("seed method missing"));

		assertEquals(1, nmz.getRequirements().getRequiredQuests().size());
		assertTrue("quest names should all resolve in the bundled data",
			nmz.getRequirements().getUnknownQuests().isEmpty());
	}
}
