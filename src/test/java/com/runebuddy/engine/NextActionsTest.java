package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.GearCategory;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NextActionsTest
{
	private NextActions next;

	@Before
	public void setUp()
	{
		next = new Advisors(DataStore.load(new Gson())).getNext();
	}

	private List<NextAction> feed(PlayerProfile profile, Goal... goals)
	{
		return next.build(profile, NextActions.Inputs.builder()
			.goals(Arrays.asList(goals))
			.style(GearCategory.MELEE)
			.settings(EngineSettings.defaults())
			.contentCategories(EnumSet.allOf(ContentCategory.class))
			.build());
	}

	private static List<NextAction.Bucket> buckets(List<NextAction> feed)
	{
		return feed.stream().map(NextAction::getBucket).collect(Collectors.toList());
	}

	@Test
	public void bucketsComeInPriorityOrder()
	{
		PlayerProfile profile = PlayerProfile.flat(60).toBuilder()
			.slayerTask(new SlayerTask("Abyssal demons", 50, 150, null))
			.build();

		List<NextAction.Bucket> order = buckets(feed(profile, Goal.skill(Skill.AGILITY, 70)));

		assertEquals(NextAction.Bucket.PINNED, order.get(0));
		assertEquals(NextAction.Bucket.GOAL, order.get(1));
		List<NextAction.Bucket> sorted = order.stream().sorted().collect(Collectors.toList());
		assertEquals("never out of bucket order", sorted, order);
		assertTrue(order.contains(NextAction.Bucket.TRAINING));
	}

	@Test
	public void slayerTaskOpensTheSlayerPage()
	{
		PlayerProfile profile = PlayerProfile.flat(60).toBuilder()
			.slayerTask(new SlayerTask("Abyssal demons", 50, 150, null))
			.build();

		NextAction pinned = feed(profile).get(0);

		assertEquals("50 Abyssal demons left", pinned.getTitle());
		assertEquals(NextAction.Destination.SKILLS, pinned.getDestination());
		assertEquals(Skill.SLAYER, pinned.getSkill());
	}

	@Test
	public void goalsSharingAStepShareOneEntry()
	{
		// Both need 43 Defence and nothing else at this level... the fire cape also wants
		// Ranged, so give the account enough of that to leave Defence as the next step.
		PlayerProfile profile = PlayerProfile.flat(30).toBuilder()
			.level(Skill.RANGED, 70).level(Skill.PRAYER, 45).level(Skill.HITPOINTS, 75)
			.build();

		List<NextAction> goals = feed(profile, Goal.content("unlock_fire_cape"), Goal.skill(Skill.DEFENCE, 43))
			.stream().filter(a -> a.getBucket() == NextAction.Bucket.GOAL).collect(Collectors.toList());

		assertEquals(1, goals.size());
		assertEquals("Train Defence to 43", goals.get(0).getTitle());
		assertEquals("Toward The fire cape and 43 Defence", goals.get(0).getDetail());
		assertTrue(goals.get(0).getHours() > 0);
	}

	@Test
	public void completedGoalsDropOutOfTheFeed()
	{
		List<NextAction> feed = feed(PlayerProfile.flat(80), Goal.skill(Skill.AGILITY, 70));

		assertFalse(buckets(feed).contains(NextAction.Bucket.GOAL));
	}

	@Test
	public void justCrossingARequirementIsAQuickWin()
	{
		PlayerProfile profile = PlayerProfile.flat(45).toBuilder()
			.level(Skill.RANGED, 62).level(Skill.HITPOINTS, 70)
			.build();

		assertTrue(feed(profile).stream().anyMatch(a ->
			a.getBucket() == NextAction.Bucket.QUICK_WIN && a.getTitle().equals("New: The fire cape")));

		// Every requirement long behind them, not just Ranged: a recent Defence level
		// could equally be what unlocked it.
		PlayerProfile longAgo = PlayerProfile.flat(60).toBuilder()
			.level(Skill.RANGED, 80).level(Skill.HITPOINTS, 85).build();
		assertFalse("ten levels on, it is not news any more", feed(longAgo).stream()
			.anyMatch(a -> a.getTitle().equals("New: The fire cape")));
	}

	@Test
	public void nearbyCombatAchievementTierIsAQuickWin()
	{
		PlayerProfile profile = PlayerProfile.flat(60).toBuilder()
			.combatAchievementPoints(295)
			.combatAchievementThreshold(CombatAchievementTier.EASY, 40)
			.combatAchievementThreshold(CombatAchievementTier.MEDIUM, 120)
			.combatAchievementThreshold(CombatAchievementTier.HARD, 300)
			.combatAchievementThreshold(CombatAchievementTier.ELITE, 800)
			.combatAchievementThreshold(CombatAchievementTier.MASTER, 1400)
			.combatAchievementThreshold(CombatAchievementTier.GRANDMASTER, 2000)
			.build();

		assertTrue(feed(profile).stream().anyMatch(a ->
			a.getTitle().equals("5 points to Hard combat achievements")));
	}

	@Test
	public void trainingSaysHowSoonTheNextLevelIs()
	{
		List<NextAction> training = feed(PlayerProfile.flat(40)).stream()
			.filter(a -> a.getBucket() == NextAction.Bucket.TRAINING).collect(Collectors.toList());

		assertFalse(training.isEmpty());
		assertTrue(training.get(0).getDetail(), training.get(0).getDetail().startsWith("Next level in "));
	}

	@Test
	public void gearIsOnlyAQuickWinWhenPriceWasChecked()
	{
		PlayerProfile profile = PlayerProfile.flat(75);

		assertFalse("no prices, no way to call it quick", feed(profile).stream()
			.anyMatch(a -> a.getDestination() == NextAction.Destination.GEAR));
		assertTrue("ironmen are not held back by price", feed(profile.toBuilder().ironman(true).build()).stream()
			.anyMatch(a -> a.getDestination() == NextAction.Destination.GEAR));
	}

	@Test
	public void freshFreeToPlayAccountStillGetsAFeed()
	{
		assertFalse(feed(PlayerProfile.flat(1).toBuilder().members(false).build()).isEmpty());
	}
}
