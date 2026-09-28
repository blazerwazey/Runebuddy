package com.runebuddy.engine;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatAchievementProgressTest
{
	/**
	 * Made-up thresholds: the real ones come from the game and the code must not care
	 * what they are.
	 */
	private static Map<CombatAchievementTier, Integer> thresholds()
	{
		Map<CombatAchievementTier, Integer> t = new EnumMap<>(CombatAchievementTier.class);
		t.put(CombatAchievementTier.EASY, 40);
		t.put(CombatAchievementTier.MEDIUM, 120);
		t.put(CombatAchievementTier.HARD, 300);
		t.put(CombatAchievementTier.ELITE, 800);
		t.put(CombatAchievementTier.MASTER, 1400);
		t.put(CombatAchievementTier.GRANDMASTER, 2000);
		return t;
	}

	@Test
	public void midwayBetweenTiers()
	{
		CombatAchievementProgress p = CombatAchievementProgress.of(142, thresholds());

		assertEquals(CombatAchievementTier.MEDIUM, p.getReached());
		assertEquals(CombatAchievementTier.HARD, p.getNext());
		assertEquals(158, p.pointsToNext());
		assertEquals("142 points, 158 to Hard", p.summary());
		assertEquals(22d / 180, p.fractionToNext(), 1e-9);
		assertFalse(p.isNextTierClose());
	}

	@Test
	public void beforeTheFirstTier()
	{
		CombatAchievementProgress p = CombatAchievementProgress.of(12, thresholds());

		assertNull(p.getReached());
		assertEquals(CombatAchievementTier.EASY, p.getNext());
		assertEquals(28, p.pointsToNext());
	}

	@Test
	public void closeWhenWithinAFifthOfTheGap()
	{
		assertTrue(CombatAchievementProgress.of(270, thresholds()).isNextTierClose());
		assertTrue("ten points is always close", CombatAchievementProgress.of(31, thresholds()).isNextTierClose());
	}

	@Test
	public void everyTierDone()
	{
		CombatAchievementProgress p = CombatAchievementProgress.of(2100, thresholds());

		assertEquals(CombatAchievementTier.GRANDMASTER, p.getReached());
		assertNull(p.getNext());
		assertFalse(p.isNextTierClose());
		assertEquals("2100 points, every tier done", p.summary());
	}

	@Test
	public void unknownUntilTheServerSendsThresholds()
	{
		Map<CombatAchievementTier, Integer> partial = thresholds();
		partial.put(CombatAchievementTier.MASTER, 0);

		assertNull(CombatAchievementProgress.of(100, partial));
		assertNull("a fresh profile has none", PlayerProfile.flat(50).combatAchievements());
	}
}
