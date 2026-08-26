package com.runebuddy.data;

import com.google.gson.Gson;
import java.util.ArrayList;
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
	public void everyTrainableSkillHasMethods()
	{
		List<String> missing = new ArrayList<>();
		for (Skill skill : Skills.trainable())
		{
			// Sailing is in the API's skill enum but the data set deliberately leaves
			// it out until its training methods are settled; see the README.
			if (skill == Skill.SAILING)
			{
				continue;
			}

			if (store.methodsFor(skill).isEmpty())
			{
				missing.add(skill.name());
			}
		}

		assertTrue("skills with no training methods: " + missing, missing.isEmpty());
	}

	@Test
	public void freeToPlayAccountsHaveSomethingForTheFreeSkills()
	{
		// A free account can train these, so leaving them members-only would leave the
		// panel blank for a large share of players.
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.MINING,
			Skill.WOODCUTTING, Skill.FISHING, Skill.COOKING, Skill.SMITHING, Skill.CRAFTING,
			Skill.MAGIC, Skill.RANGED, Skill.PRAYER, Skill.FIREMAKING})
		{
			boolean anyFree = store.methodsFor(skill).stream().anyMatch(m -> !m.isMembers());
			assertTrue(skill + " has no free-to-play method", anyFree);
		}
	}

	@Test
	public void ironmenHaveSomethingForEverySkill()
	{
		List<String> missing = new ArrayList<>();
		for (Skill skill : Skills.trainable())
		{
			if (skill == Skill.SAILING)
			{
				continue;
			}

			if (store.methodsFor(skill).stream().noneMatch(TrainingMethod::isIronmanFriendly))
			{
				missing.add(skill.name());
			}
		}

		assertTrue("skills with nothing an ironman can do: " + missing, missing.isEmpty());
	}

	@Test
	public void everySkillHasAMethodAvailableFromLevelOne()
	{
		List<String> missing = new ArrayList<>();
		for (Skill skill : Skills.trainable())
		{
			if (skill == Skill.SAILING)
			{
				continue;
			}

			int lowest = store.methodsFor(skill).stream()
				.mapToInt(TrainingMethod::getMinLevel)
				.min()
				.orElse(Integer.MAX_VALUE);

			// A brand new account should not open a skill tab and be told nothing is
			// available. Some skills genuinely start above their floor (Herblore needs
			// Druidic Ritual first), so allow a small opening gap.
			if (lowest > Skills.startingLevel(skill) + 4)
			{
				missing.add(skill.name() + " starts at " + lowest);
			}
		}

		assertTrue("skills a new account cannot start: " + missing, missing.isEmpty());
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
	public void everyCombatStyleCoversTheMainSlots()
	{
		// These are the slots the gear tab shows for a combat ladder; a gap means an
		// empty row for anyone training that style.
		EquipSlot[] core = {EquipSlot.HEAD, EquipSlot.BODY, EquipSlot.LEGS, EquipSlot.WEAPON,
			EquipSlot.HANDS, EquipSlot.FEET, EquipSlot.NECK, EquipSlot.RING};

		for (GearCategory category : new GearCategory[]{GearCategory.MELEE, GearCategory.RANGED,
			GearCategory.MAGIC})
		{
			for (EquipSlot slot : core)
			{
				assertFalse(category + " has no " + slot + " ladder",
					store.ladder(category, slot).isEmpty());
			}
		}
	}

	@Test
	public void everyLadderStartsSomewhereReachable()
	{
		for (GearItem item : store.getGear())
		{
			for (java.util.Map.Entry<Skill, Integer> req : item.getRequirements().getSkillLevels().entrySet())
			{
				assertTrue(item.getName() + " requires an impossible level in " + req.getKey(),
					req.getValue() >= 1 && req.getValue() <= 99);
			}
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
