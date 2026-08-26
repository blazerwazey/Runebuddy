package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GearAdvisorTest
{
	private static final int RUNE_SCIMITAR = 1333;
	private static final int DRAGON_SCIMITAR = 4587;
	private static final int ABYSSAL_WHIP = 4151;

	private GearAdvisor advisor;

	@Before
	public void setUp()
	{
		advisor = new GearAdvisor(DataStore.load(new Gson()));
	}

	private static GearSuggestion slot(List<GearSuggestion> suggestions, EquipSlot slot)
	{
		return suggestions.stream()
			.filter(s -> s.getSlot() == slot)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no suggestion for " + slot));
	}

	private static GearSuggestion tool(List<GearSuggestion> suggestions, Skill skill)
	{
		return suggestions.stream()
			.filter(s -> s.getToolFor() == skill)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no tool suggestion for " + skill));
	}

	@Test
	public void proposesTheNextRungUpFromWhatYouOwn()
	{
		PlayerProfile player = PlayerProfile.flat(60).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.owned(RUNE_SCIMITAR, 1)
			.build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, player, null), EquipSlot.WEAPON);

		assertEquals(RUNE_SCIMITAR, weapon.getOwned().getItemId());
		assertNotNull("60 Attack with the quest done unlocks the dragon scimitar", weapon.getNext());
		assertEquals(DRAGON_SCIMITAR, weapon.getNext().getItemId());
		assertEquals("the dragon scimitar is also the best they qualify for",
			DRAGON_SCIMITAR, weapon.getGoal().getItemId());
	}

	@Test
	public void namesTheRequirementBlockingTheNextTier()
	{
		PlayerProfile player = PlayerProfile.flat(60).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.owned(RUNE_SCIMITAR, 1)
			.build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, player, null), EquipSlot.WEAPON);

		assertNotNull(weapon.getLocked());
		assertEquals(ABYSSAL_WHIP, weapon.getLocked().getItemId());
		assertEquals("needs 70 Attack", weapon.getLockedReport().blockingSummary());
	}

	@Test
	public void aQuestLockKeepsAnItemOutOfReach()
	{
		// 60 Attack is enough for the dragon scimitar on paper, but not without the quest.
		PlayerProfile noQuest = PlayerProfile.flat(60);

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, noQuest, null), EquipSlot.WEAPON);

		assertEquals("the rune scimitar is the ceiling until the quest is done",
			RUNE_SCIMITAR, weapon.getGoal().getItemId());
		assertEquals(DRAGON_SCIMITAR, weapon.getLocked().getItemId());
		assertTrue(weapon.getLockedReport().blockingSummary().contains("Monkey Madness"));
	}

	@Test
	public void aLowLevelAccountIsNotShownGearItCannotUse()
	{
		PlayerProfile fresh = PlayerProfile.flat(10);

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, fresh, null), EquipSlot.WEAPON);

		assertEquals("only the steel longsword is within reach at 10 Attack",
			1291, weapon.getGoal().getItemId());
		assertEquals("the rune scimitar is the thing to aim for",
			RUNE_SCIMITAR, weapon.getLocked().getItemId());
	}

	@Test
	public void freeToPlayLaddersStopAtFreeToPlayGear()
	{
		PlayerProfile f2p = PlayerProfile.flat(80).toBuilder().members(false).build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, f2p, null), EquipSlot.WEAPON);

		assertEquals(RUNE_SCIMITAR, weapon.getGoal().getItemId());
		assertNull("there is no members gear to aim for on a free account", weapon.getLocked());
	}

	@Test
	public void ownersOfTheBestAvailableItemAreToldTheyAreDone()
	{
		PlayerProfile player = PlayerProfile.flat(60).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.owned(DRAGON_SCIMITAR, 1)
			.build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, player, null), EquipSlot.WEAPON);

		assertNull("nothing further to buy at this level", weapon.getNext());
		assertTrue(weapon.isSatisfied());
	}

	@Test
	public void equippedItemsAreRecognised()
	{
		PlayerProfile player = PlayerProfile.flat(60).toBuilder()
			.owned(RUNE_SCIMITAR, 1)
			.equipped(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx(), RUNE_SCIMITAR)
			.build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, player, null), EquipSlot.WEAPON);

		assertNotNull(weapon.getEquipped());
		assertEquals(RUNE_SCIMITAR, weapon.getEquipped().getItemId());
	}

	@Test
	public void toolLaddersFollowTheSameRules()
	{
		PlayerProfile miner = PlayerProfile.flat(60).toBuilder()
			.level(Skill.MINING, 61)
			.build();

		List<GearSuggestion> tools = advisor.adviseTools(miner, null);
		assertFalse("tools should be suggested", tools.isEmpty());

		GearSuggestion pickaxe = tool(tools, Skill.MINING);
		assertEquals("61 Mining and 60 Attack reaches the dragon pickaxe",
			11920, pickaxe.getGoal().getItemId());
		assertEquals("a tool row is labelled by its skill", "Mining", pickaxe.label());
	}

	@Test
	public void toolsRespectTheirWieldRequirement()
	{
		// Plenty of Mining, but not enough Attack to wield the dragon pickaxe.
		PlayerProfile miner = PlayerProfile.flat(30).toBuilder()
			.level(Skill.MINING, 70)
			.build();

		GearSuggestion pickaxe = tool(advisor.adviseTools(miner, null), Skill.MINING);

		assertEquals("40 Attack is still missing for the rune pickaxe",
			1275, pickaxe.getLocked().getItemId());
		assertTrue(pickaxe.getLockedReport().blockingSummary().contains("Attack"));
	}

	@Test
	public void styleDetectionFollowsTheHighestInvestedSkill()
	{
		assertEquals(GearCategory.MELEE, GearAdvisor.detectStyle(PlayerProfile.flat(50)));

		assertEquals(GearCategory.RANGED, GearAdvisor.detectStyle(
			PlayerProfile.flat(40).toBuilder().level(Skill.RANGED, 80).build()));

		assertEquals(GearCategory.MAGIC, GearAdvisor.detectStyle(
			PlayerProfile.flat(40).toBuilder().level(Skill.MAGIC, 80).build()));
	}
}
