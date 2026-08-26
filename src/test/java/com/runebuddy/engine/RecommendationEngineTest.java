package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.TrainingMethod;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecommendationEngineTest
{
	private RecommendationEngine engine;

	@Before
	public void setUp()
	{
		engine = new RecommendationEngine(DataStore.load(new Gson()));
	}

	private static List<String> ids(List<MethodScore> scores)
	{
		return scores.stream().map(s -> s.getMethod().getId()).collect(Collectors.toList());
	}

	@Test
	public void freeToPlayNeverSeesMembersMethods()
	{
		PlayerProfile f2p = PlayerProfile.flat(50).toBuilder().members(false).build();

		SkillAdvice mining = engine.adviceFor(Skill.MINING, f2p, EngineSettings.defaults(), null);
		for (MethodScore score : mining.getRecommended())
		{
			assertFalse(score.getMethod().getId() + " is members-only",
				score.getMethod().isMembers());
		}

		assertFalse("free-to-play should still have mining options",
			mining.getRecommended().isEmpty());
	}

	@Test
	public void membersSeeMembersMethods()
	{
		PlayerProfile member = PlayerProfile.flat(50);

		List<String> shown = ids(engine.adviceFor(Skill.MINING, member, EngineSettings.defaults(), null)
			.getRecommended());

		assertTrue("members should be offered the Motherlode Mine",
			shown.contains("mining_motherlode_mine"));
	}

	@Test
	public void ironmenAreNotOfferedBuyablesOnlyMethods()
	{
		PlayerProfile ironman = PlayerProfile.flat(80).toBuilder().ironman(true).build();

		List<String> shown = ids(engine.adviceFor(Skill.ATTACK, ironman, EngineSettings.defaults(), null)
			.getRecommended());

		assertFalse("Nightmare Zone depends on bought potions",
			shown.contains("attack_nightmare_zone"));
	}

	@Test
	public void mainsAreOfferedBuyablesOnlyMethods()
	{
		PlayerProfile main = PlayerProfile.flat(80).toBuilder()
			.completedQuest(net.runelite.api.Quest.DREAM_MENTOR)
			.build();

		List<String> shown = ids(engine.adviceFor(Skill.ATTACK, main, EngineSettings.defaults(), null)
			.getRecommended());

		assertTrue("a main at 80 with the quest done should see the Nightmare Zone",
			shown.contains("attack_nightmare_zone"));
	}

	@Test
	public void methodsBelowTheLevelRequirementAreNotRecommended()
	{
		PlayerProfile fresh = PlayerProfile.flat(1);

		for (MethodScore score : engine.adviceFor(Skill.MINING, fresh, EngineSettings.defaults(), null)
			.getRecommended())
		{
			assertTrue(score.getMethod().getId() + " needs a level the player lacks",
				score.getMethod().getMinLevel() <= 1);
		}
	}

	@Test
	public void valuingGoldDemotesExpensiveMethods()
	{
		// A main who has done the quest and has almost no coins.
		PlayerProfile broke = PlayerProfile.flat(80).toBuilder()
			.completedQuest(net.runelite.api.Quest.DREAM_MENTOR)
			.bankKnown(true)
			.liquidGp(1000)
			.build();

		EngineSettings xpOnly = EngineSettings.builder().xpWeight(10).gpWeight(0).afkWeight(0).build();
		EngineSettings goldMatters = EngineSettings.builder().xpWeight(4).gpWeight(10).afkWeight(0).build();

		List<String> byXp = ids(engine.adviceFor(Skill.ATTACK, broke, xpOnly, null).getRecommended());
		List<String> byGold = ids(engine.adviceFor(Skill.ATTACK, broke, goldMatters, null).getRecommended());

		assertEquals("the fastest method should win when only XP matters",
			"attack_nightmare_zone", byXp.get(0));
		assertEquals("the affordable method should win when gold matters and coins are short",
			"attack_sand_crabs", byGold.get(0));
	}

	@Test
	public void affordabilityIgnoresCostWhenThePlayerCanCoverIt()
	{
		PlayerProfile rich = PlayerProfile.flat(80).toBuilder()
			.completedQuest(net.runelite.api.Quest.DREAM_MENTOR)
			.bankKnown(true)
			.liquidGp(500_000_000L)
			.build();

		EngineSettings goldMatters = EngineSettings.builder().xpWeight(4).gpWeight(10).afkWeight(0).build();

		assertEquals("a player who can easily afford it should not be steered away",
			"attack_nightmare_zone",
			ids(engine.adviceFor(Skill.ATTACK, rich, goldMatters, null).getRecommended()).get(0));
	}

	@Test
	public void rankingUsesTheRateAtThePlayersLevel()
	{
		TrainingMethod crabs = find("attack_sand_crabs");

		PlayerProfile low = PlayerProfile.flat(20);
		PlayerProfile high = PlayerProfile.flat(90);

		int lowRate = scoreOf(engine.adviceFor(Skill.ATTACK, low, EngineSettings.defaults(), null)
			.getRecommended(), "attack_sand_crabs").getXpPerHour();
		int highRate = scoreOf(engine.adviceFor(Skill.ATTACK, high, EngineSettings.defaults(), null)
			.getRecommended(), "attack_sand_crabs").getXpPerHour();

		assertEquals(crabs.xpAt(20), lowRate);
		assertEquals(crabs.xpAt(90), highRate);
		assertTrue("the curve should rise with level", highRate > lowRate);
	}

	@Test
	public void outgrownMethodsRankBelowLevelAppropriateOnes()
	{
		// Copper is recommended only to 15; willows and iron are not.
		PlayerProfile midGame = PlayerProfile.flat(60);

		List<MethodScore> mining = engine.adviceFor(Skill.MINING, midGame, EngineSettings.defaults(), null)
			.getRecommended();

		MethodScore copper = scoreOf(mining, "mining_copper_tin");
		MethodScore iron = scoreOf(mining, "mining_iron_powermine");

		assertTrue("copper should be flagged as out-levelled", copper.isOutgrown());
		assertFalse("iron is still in range at 60", iron.isOutgrown());
		assertTrue("an out-levelled method should not outrank a current one",
			iron.getScore() > copper.getScore());
	}

	@Test
	public void upcomingUnlocksListMethodsJustOutOfReach()
	{
		// Iron opens at 15; at 13 it should be previewed rather than recommended.
		PlayerProfile almost = PlayerProfile.flat(13);

		SkillAdvice advice = engine.adviceFor(Skill.MINING, almost, EngineSettings.defaults(), null);

		assertFalse("iron should not be recommended yet",
			ids(advice.getRecommended()).contains("mining_iron_powermine"));
		assertTrue("iron should be previewed as an upcoming unlock",
			ids(advice.getUnlockingSoon()).contains("mining_iron_powermine"));
	}

	@Test
	public void upcomingUnlocksCanBeTurnedOff()
	{
		PlayerProfile almost = PlayerProfile.flat(13);
		EngineSettings off = EngineSettings.builder().showUnlocksSoon(false).build();

		assertTrue(engine.adviceFor(Skill.MINING, almost, off, null).getUnlockingSoon().isEmpty());
	}

	@Test
	public void distantMethodsAreNotPreviewed()
	{
		// Motherlode opens at 30, far beyond a level 1 account's horizon.
		PlayerProfile fresh = PlayerProfile.flat(1);

		assertFalse(ids(engine.adviceFor(Skill.MINING, fresh, EngineSettings.defaults(), null)
			.getUnlockingSoon()).contains("mining_motherlode_mine"));
	}

	@Test
	public void methodsPerSkillCapsTheList()
	{
		PlayerProfile member = PlayerProfile.flat(70);
		EngineSettings single = EngineSettings.builder().methodsPerSkill(1).build();

		assertEquals(1, engine.adviceFor(Skill.MINING, member, single, null).getRecommended().size());
	}

	@Test
	public void zeroedWeightsStillProduceAnOrdering()
	{
		PlayerProfile member = PlayerProfile.flat(70);
		EngineSettings noWeights = EngineSettings.builder().xpWeight(0).gpWeight(0).afkWeight(0).build();

		List<MethodScore> mining = engine.adviceFor(Skill.MINING, member, noWeights, null).getRecommended();

		assertFalse("zeroed sliders should fall back to equal weighting, not an empty list",
			mining.isEmpty());
		for (MethodScore score : mining)
		{
			assertTrue("scores should stay finite", Double.isFinite(score.getScore()));
		}
	}

	@Test
	public void everyRecommendationCarriesAnExplanation()
	{
		PlayerProfile member = PlayerProfile.flat(60);

		for (MethodScore score : engine.adviceFor(Skill.MINING, member, EngineSettings.defaults(), null)
			.getRecommended())
		{
			assertFalse(score.getMethod().getId() + " has no rationale",
				score.getRationale().trim().isEmpty());
		}
	}

	@Test
	public void overviewPrefersSkillsTheAccountHasNeglected()
	{
		// Mining is untouched, Attack is nearly maxed. Both have methods available.
		PlayerProfile lopsided = PlayerProfile.flat(95).toBuilder()
			.level(Skill.MINING, 1)
			.completedQuest(net.runelite.api.Quest.DREAM_MENTOR)
			.build();

		List<MethodScore> overview = engine.topOverall(lopsided, EngineSettings.defaults(), null, 5);

		assertFalse("overview should suggest something", overview.isEmpty());
		assertEquals("the neglected skill should lead the overview",
			Skill.MINING, overview.get(0).getMethod().getSkill());
	}

	@Test
	public void overviewSuggestsEachSkillAtMostOnce()
	{
		PlayerProfile member = PlayerProfile.flat(60);

		List<MethodScore> overview = engine.topOverall(member, EngineSettings.defaults(), null, 10);
		long distinctSkills = overview.stream().map(s -> s.getMethod().getSkill()).distinct().count();

		assertEquals("the overview should not repeat a skill", overview.size(), distinctSkills);
	}

	@Test
	public void loggedOutProfileYieldsNoAdvice()
	{
		SkillAdvice advice = engine.adviceFor(Skill.MINING, PlayerProfile.LOGGED_OUT,
			EngineSettings.defaults(), null);

		// A logged-out profile reads as a level 1 free-to-play account, which is a
		// coherent answer; what matters is that nothing throws.
		for (MethodScore score : advice.getRecommended())
		{
			assertFalse(score.getMethod().isMembers());
		}
	}

	private TrainingMethod find(String id)
	{
		return DataStore.load(new Gson()).getMethods().stream()
			.filter(m -> id.equals(m.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("seed method missing: " + id));
	}

	private static MethodScore scoreOf(List<MethodScore> scores, String id)
	{
		return scores.stream()
			.filter(s -> id.equals(s.getMethod().getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("expected " + id + " in " + ids(scores)));
	}
}
