package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.ContentActivity;
import com.runebuddy.data.ContentCategory;
import com.runebuddy.data.DataStore;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContentAdvisorTest
{
	private static final Set<ContentCategory> ALL = EnumSet.allOf(ContentCategory.class);

	private ContentAdvisor advisor;

	@Before
	public void setUp()
	{
		advisor = new ContentAdvisor(DataStore.load(new Gson()));
	}

	private static List<String> ids(List<ContentSuggestion> suggestions)
	{
		return suggestions.stream()
			.map(s -> s.getActivity().getId())
			.collect(Collectors.toList());
	}

	@Test
	public void aFreeAccountHasSomethingToDo()
	{
		// Almost every activity is members-only, so without deliberate free-to-play
		// entries a free account opens the tab to nothing at all.
		PlayerProfile f2p = PlayerProfile.flat(50).toBuilder().members(false).build();
		ContentAdvice advice = advisor.advise(f2p, ALL, null);

		assertFalse("a free account should be ready for something", advice.getReady().isEmpty());
	}

	@Test
	public void aFreshAccountIsReadyForVeryLittle()
	{
		ContentAdvice advice = advisor.advise(PlayerProfile.flat(1), ALL, null);

		for (ContentSuggestion suggestion : advice.getReady())
		{
			assertTrue(suggestion.getActivity().getId() + " should not be ready at level 1",
				suggestion.getRequirements().isSatisfied());
		}

		assertFalse("there should still be plenty to aim at", advice.getLocked().isEmpty());
	}

	@Test
	public void aStrongAccountIsReadyForMore()
	{
		ContentAdvice low = advisor.advise(PlayerProfile.flat(30), ALL, null);
		ContentAdvice high = advisor.advise(PlayerProfile.flat(95), ALL, null);

		assertTrue("a stronger account should unlock more, not less",
			high.getReady().size() > low.getReady().size());
	}

	@Test
	public void freeToPlayNeverSeesMembersContent()
	{
		PlayerProfile f2p = PlayerProfile.flat(70).toBuilder().members(false).build();
		ContentAdvice advice = advisor.advise(f2p, ALL, null);

		for (List<ContentSuggestion> group : List.of(advice.getReady(), advice.getClose(), advice.getLocked()))
		{
			for (ContentSuggestion suggestion : group)
			{
				assertFalse(suggestion.getActivity().getId() + " is members-only",
					suggestion.getActivity().isMembers());
			}
		}
	}

	@Test
	public void ironmenAreNotOfferedContentThatDependsOnBuyingIn()
	{
		PlayerProfile ironman = PlayerProfile.flat(90).toBuilder().ironman(true).build();
		ContentAdvice advice = advisor.advise(ironman, ALL, null);

		assertFalse("Nightmare Zone runs on bought potions",
			ids(advice.getReady()).contains("nightmare_zone"));
	}

	@Test
	public void categoriesAreFiltered()
	{
		ContentAdvice raidsOnly = advisor.advise(
			PlayerProfile.flat(90), EnumSet.of(ContentCategory.RAID), null);

		for (ContentSuggestion suggestion : raidsOnly.getReady())
		{
			assertTrue(suggestion.getActivity().getCategory() == ContentCategory.RAID);
		}

		assertTrue("nothing should come back when no category is selected",
			advisor.advise(PlayerProfile.flat(90), EnumSet.noneOf(ContentCategory.class), null)
				.isEmpty());
	}

	@Test
	public void gearIsCheckedAsWellAsLevels()
	{
		// A maxed account in nothing at all meets the stated levels for the Fight Caves,
		// but is in no position to attempt it. That is exactly the case levels alone miss.
		int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();

		PlayerProfile underGeared = PlayerProfile.flat(95).toBuilder()
			.itemStats(1, new EquipmentStats(weaponSlot,
				1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0f, 0))
			.build();

		ContentSuggestion caves = find(advisor.advise(underGeared, ALL, null), "fight_caves");

		assertNotNull("the Fight Caves should be listed somewhere", caves);
		assertFalse("bare stats should not read as ready", caves.isGearReady());
		assertNotNull("and it should say why", caves.getGearAdvice());
		assertTrue(caves.getGearAdvice().toLowerCase().contains("light"));
	}

	@Test
	public void gearIsNotJudgedBeforeWeHaveSeenAny()
	{
		// With no bank ever opened there are no stats to judge, and telling someone they
		// are under-geared on no evidence is worse than staying quiet.
		ContentSuggestion caves = find(advisor.advise(PlayerProfile.flat(95), ALL, null), "fight_caves");

		assertNotNull(caves);
		assertTrue(caves.isGearReady());
		assertNull(caves.getGearAdvice());
	}

	@Test
	public void everyActivityExplainsWhyYouWouldGo()
	{
		for (ContentActivity activity : DataStore.load(new Gson()).getContent())
		{
			assertNotNull(activity.getId() + " has no rewards", activity.getRewards());
			assertFalse(activity.getId() + " has empty rewards", activity.getRewards().trim().isEmpty());
			assertNotNull(activity.getId() + " has no category", activity.getCategory());
		}
	}

	@Test
	public void everyCategoryHasSomethingInIt()
	{
		DataStore data = DataStore.load(new Gson());
		for (ContentCategory category : ContentCategory.values())
		{
			assertFalse(category + " has no activities, so its filter would show nothing",
				data.contentIn(category).isEmpty());
		}
	}

	private static ContentSuggestion find(ContentAdvice advice, String id)
	{
		for (List<ContentSuggestion> group : List.of(advice.getReady(), advice.getClose(), advice.getLocked()))
		{
			for (ContentSuggestion suggestion : group)
			{
				if (id.equals(suggestion.getActivity().getId()))
				{
					return suggestion;
				}
			}
		}

		return null;
	}
}
