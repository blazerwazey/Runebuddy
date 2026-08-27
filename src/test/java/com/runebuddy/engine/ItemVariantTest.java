package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The data files name one id per item, but a player holds whichever charge or condition
 * they happen to have. Everything here is about those lining up.
 */
public class ItemVariantTest
{
	private static final int AMULET_OF_GLORY_4 = 1712;
	private static final int AMULET_OF_GLORY_6 = 11978;
	private static final int AMULET_OF_GLORY_UNCHARGED = 1704;
	private static final int RING_OF_DUELING_8 = 2552;
	private static final int RING_OF_DUELING_7 = 2554;
	private static final int RUNE_SCIMITAR = 1333;
	private static final int DRAGON_SCIMITAR = 4587;

	/**
	 * Builds a profile the way {@link ProfileTracker} does, storing the canonical id.
	 */
	private static PlayerProfile owning(int heldItemId)
	{
		return PlayerProfile.flat(70).toBuilder()
			.owned(PlayerProfile.canonicalItem(heldItemId), 1)
			.build();
	}

	@Test
	public void aDifferentChargeIsStillTheSameAmulet()
	{
		assertTrue("a glory(6) should satisfy the glory the data names",
			owning(AMULET_OF_GLORY_6).owns(AMULET_OF_GLORY_4));

		assertTrue("an uncharged glory is still a glory",
			owning(AMULET_OF_GLORY_UNCHARGED).owns(AMULET_OF_GLORY_4));

		assertTrue(owning(RING_OF_DUELING_7).owns(RING_OF_DUELING_8));
	}

	@Test
	public void wornGearIsMatchedAcrossCharges()
	{
		int neck = EquipmentInventorySlot.AMULET.getSlotIdx();

		PlayerProfile profile = PlayerProfile.flat(70).toBuilder()
			.equipped(neck, PlayerProfile.canonicalItem(AMULET_OF_GLORY_6))
			.build();

		assertTrue("wearing a glory(6) counts as wearing the glory in the data",
			profile.isWearing(neck, AMULET_OF_GLORY_4));
	}

	@Test
	public void differentItemsAreNeverCollapsed()
	{
		assertFalse("a rune scimitar must not pass for a dragon scimitar",
			owning(RUNE_SCIMITAR).owns(DRAGON_SCIMITAR));

		assertFalse("nor the other way round",
			owning(DRAGON_SCIMITAR).owns(RUNE_SCIMITAR));
	}

	@Test
	public void anUnknownItemCanonicalisesToItself()
	{
		// Anything not in the variation table has to pass straight through rather than
		// collapsing to zero, or every unlisted item would match every other one.
		assertEquals(RUNE_SCIMITAR, PlayerProfile.canonicalItem(RUNE_SCIMITAR));
		assertFalse(PlayerProfile.flat(70).owns(RUNE_SCIMITAR));
	}

	@Test
	public void aChargedVariantSatisfiesTheGearLadder()
	{
		// End to end: holding a glory(6), the advisor should register the neck slot as
		// filled rather than reporting nothing owned.
		GearAdvisor advisor = new GearAdvisor(DataStore.load(new Gson()));

		GearSuggestion neck = advisor.adviseCombat(GearCategory.MELEE, owning(AMULET_OF_GLORY_6), null)
			.stream()
			.filter(s -> s.getSlot() == EquipSlot.NECK)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no neck suggestion"));

		assertNotNull("the glory(6) should register as owned", neck.getOwned());
		assertEquals(AMULET_OF_GLORY_4, neck.getOwned().getItemId());
	}
}
