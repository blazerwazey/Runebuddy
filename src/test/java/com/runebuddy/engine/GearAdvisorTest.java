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
	private static final int BLACK_SCIMITAR = 1327;
	private static final int MITHRIL_SCIMITAR = 1329;
	private static final int RUNE_SCIMITAR = 1333;
	private static final int DRAGON_SCIMITAR = 4587;
	private static final int ABYSSAL_WHIP = 4151;
	private static final int DRAGON_PICKAXE = 11920;
	private static final int INFERNAL_PICKAXE = 13243;
	private static final int RUNE_PICKAXE = 1275;
	private static final int FIGHTER_TORSO = 10551;
	private static final int GHRAZI_RAPIER = 22324;

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
	public void doesNotProposeAnItemWorseThanWhatYouAlreadyOwn()
	{
		// Owning the top of the ladder, there is nothing to buy even though the ladder
		// has plenty of cheaper rungs below.
		PlayerProfile player = PlayerProfile.flat(60).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.owned(DRAGON_SCIMITAR, 1)
			.build();

		GearSuggestion weapon = slot(advisor.adviseCombat(GearCategory.MELEE, player, null), EquipSlot.WEAPON);

		assertNull(weapon.getNext());
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

		assertEquals("the black scimitar is the best within reach at 10 Attack",
			BLACK_SCIMITAR, weapon.getGoal().getItemId());
		assertEquals("the next rung up is the thing to aim for",
			MITHRIL_SCIMITAR, weapon.getLocked().getItemId());
		assertEquals("needs 20 Attack", weapon.getLockedReport().blockingSummary());
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
		assertEquals("the infernal pickaxe shares the dragon's requirements and outranks it",
			INFERNAL_PICKAXE, pickaxe.getGoal().getItemId());
		assertEquals("owning nothing, the thing to buy is the best they qualify for",
			INFERNAL_PICKAXE, pickaxe.getNext().getItemId());
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
			RUNE_PICKAXE, pickaxe.getLocked().getItemId());
		assertTrue(pickaxe.getLockedReport().blockingSummary().contains("Attack"));
	}

	@Test
	public void ironmenAreNotFilteredByWhatTheyCanAfford()
	{
		// Coins buy nothing on an ironman, so the best they qualify for is the answer
		// regardless of how little gold they are holding.
		PlayerProfile ironman = PlayerProfile.flat(75).toBuilder()
			.ironman(true)
			.bankKnown(true)
			.liquidGp(0)
			.build();

		GearAdvisor.PriceResolver expensive = itemId -> 100_000_000;

		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, ironman, null, expensive), EquipSlot.WEAPON);

		assertEquals("price should not hold an ironman back from their ceiling",
			weapon.getGoal().getItemId(), weapon.getNext().getItemId());
	}

	@Test
	public void mainsAreStillFilteredByWhatTheyCanAfford()
	{
		// The same levels as a main, with enough gold for a middling rung but not the
		// top one, must still be held back to what they can pay for.
		PlayerProfile main = PlayerProfile.flat(75).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.bankKnown(true)
			.liquidGp(100_000)
			.build();

		// Everything above the dragon scimitar is priced out of reach.
		GearAdvisor.PriceResolver steep = itemId ->
			itemId == ABYSSAL_WHIP || itemId == GHRAZI_RAPIER ? 100_000_000 : 50_000;

		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, main, null, steep), EquipSlot.WEAPON);

		assertEquals("the affordable rung is what to buy",
			DRAGON_SCIMITAR, weapon.getNext().getItemId());
		assertEquals("the ceiling is still named separately",
			GHRAZI_RAPIER, weapon.getGoal().getItemId());
	}

	@Test
	public void aMainWhoCanAffordNothingIsStillShownTheGoal()
	{
		// With no gold at all there is no cheaper rung to fall back to, so naming the
		// target beats naming nothing.
		PlayerProfile broke = PlayerProfile.flat(75).toBuilder()
			.bankKnown(true)
			.liquidGp(0)
			.build();

		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, broke, null, itemId -> 100_000_000),
			EquipSlot.WEAPON);

		assertEquals(weapon.getGoal().getItemId(), weapon.getNext().getItemId());
	}

	@Test
	public void untradeablesAreNeverFilteredOutOnPrice()
	{
		// A fighter torso cannot be bought, so no amount of poverty rules it out.
		PlayerProfile main = PlayerProfile.flat(45).toBuilder()
			.bankKnown(true)
			.liquidGp(0)
			.build();

		GearSuggestion body = slot(
			advisor.adviseCombat(GearCategory.MELEE, main, null, itemId -> 100_000_000), EquipSlot.BODY);

		assertEquals("the fighter torso is earned, not bought",
			FIGHTER_TORSO, body.getNext().getItemId());
	}

	@Test
	public void ironmenMustMeetTheRequirementsOfObtainingAnItemThemselves()
	{
		// 70 Attack is all a main needs to wield a whip. An ironman also has to be able
		// to farm one, which means 85 Slayer. The quest is granted so that the dragon
		// scimitar is cleared out of the way and the whip is the rung under test.
		PlayerProfile.PlayerProfileBuilder base = PlayerProfile.flat(70).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.bankKnown(true)
			.liquidGp(500_000_000L);

		GearSuggestion asMain = slot(
			advisor.adviseCombat(GearCategory.MELEE, base.build(), null, null), EquipSlot.WEAPON);
		assertEquals("a main with the gold and the level can just buy one",
			ABYSSAL_WHIP, asMain.getGoal().getItemId());

		GearSuggestion asIronman = slot(
			advisor.adviseCombat(GearCategory.MELEE, base.ironman(true).build(), null, null),
			EquipSlot.WEAPON);

		assertEquals("the ceiling should drop to the scimitar without the Slayer level",
			DRAGON_SCIMITAR, asIronman.getGoal().getItemId());
		assertEquals("the whip becomes the thing to aim for",
			ABYSSAL_WHIP, asIronman.getLocked().getItemId());
		assertTrue("the blocker should name the Slayer level, not a price: "
				+ asIronman.getLockedReport().blockingSummary(),
			asIronman.getLockedReport().blockingSummary().contains("85 Slayer"));
	}

	@Test
	public void anIronmanWhoMeetsTheGateIsOfferedTheItem()
	{
		PlayerProfile ironman = PlayerProfile.flat(70).toBuilder()
			.ironman(true)
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.level(Skill.SLAYER, 85)
			.build();

		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, ironman, null, null), EquipSlot.WEAPON);

		assertEquals(ABYSSAL_WHIP, weapon.getGoal().getItemId());
	}

	@Test
	public void ironmanGatesDoNotLeakOntoMains()
	{
		// The Slayer level attached to the whip must apply to ironmen only.
		PlayerProfile main = PlayerProfile.flat(70).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.level(Skill.SLAYER, 1)
			.build();

		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, main, null, null), EquipSlot.WEAPON);

		assertEquals(ABYSSAL_WHIP, weapon.getGoal().getItemId());
	}

	@Test
	public void pricesAndNamesComeFromTheSnapshot()
	{
		// The panel must never reach into the client for these: both readings go through
		// item definitions, which are client-thread only. They are resolved when the
		// snapshot is taken and read back from it here.
		PlayerProfile profile = PlayerProfile.flat(75).toBuilder()
			.completedQuest(Quest.MONKEY_MADNESS_I)
			.bankKnown(true)
			.liquidGp(100_000)
			.itemName(ABYSSAL_WHIP, "Abyssal whip")
			.itemPrice(ABYSSAL_WHIP, 100_000_000)
			.itemPrice(DRAGON_SCIMITAR, 50_000)
			.build();

		assertEquals("Abyssal whip", profile.nameOf(ABYSSAL_WHIP));
		assertEquals(100_000_000, profile.priceOf(ABYSSAL_WHIP));
		assertEquals("an unresolved price reads as zero, not a crash", 0, profile.priceOf(GHRAZI_RAPIER));
		assertNull("an unresolved name reads as null", profile.nameOf(GHRAZI_RAPIER));

		// Driving the advisor off those resolvers has to behave the same as a stub.
		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, profile, profile.itemNameResolver(),
				profile.priceResolver()),
			EquipSlot.WEAPON);

		assertEquals("the whip is out of budget at the snapshot's price",
			DRAGON_SCIMITAR, weapon.getNext().getItemId());
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
