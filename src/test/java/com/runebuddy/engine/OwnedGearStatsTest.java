package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Ranking what the player owns has to work from the client's live equipment data rather
 * than from anything hand-written, or gear released after this plugin was written reads
 * as "nothing here yet" however good it is.
 */
public class OwnedGearStatsTest
{
	/**
	 * A real item, newer than the curated ladders, and the case that prompted all this.
	 */
	private static final int CUSTODIAN_ANTLER_GUARD = 31081;

	private static final int RUNE_KITESHIELD = 1201;
	private static final int SHIELD_SLOT = EquipmentInventorySlot.SHIELD.getSlotIdx();
	private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();

	private GearAdvisor advisor;

	@Before
	public void setUp()
	{
		advisor = new GearAdvisor(DataStore.load(new Gson()));
	}

	private static EquipmentStats stats(int slotIdx, int strength, int rangedStrength,
										float magicDamage, int magicAttack, int rangedAttack)
	{
		return new EquipmentStats(slotIdx,
			0, 0, 0, magicAttack, rangedAttack,
			0, 0, 0, 0, 0,
			strength, rangedStrength, magicDamage, 0);
	}

	private static GearSuggestion slot(List<GearSuggestion> suggestions, EquipSlot slot)
	{
		return suggestions.stream()
			.filter(s -> s.getSlot() == slot)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no suggestion for " + slot));
	}

	@Test
	public void anItemNoDataFileMentionsIsStillRecognised()
	{
		PlayerProfile profile = PlayerProfile.flat(90).toBuilder()
			.owned(CUSTODIAN_ANTLER_GUARD, 1)
			.itemName(CUSTODIAN_ANTLER_GUARD, "Custodian antler guard")
			.itemStats(CUSTODIAN_ANTLER_GUARD, stats(SHIELD_SLOT, 0, 0, 5f, 30, 25))
			.build();

		GearSuggestion shield = slot(
			advisor.adviseCombat(GearCategory.MAGIC, profile, null), EquipSlot.SHIELD);

		assertEquals("the shield the player actually owns should be named",
			"Custodian antler guard", shield.getBestOwnedName());
		assertNotNull(shield.getBestOwnedItemId());
	}

	@Test
	public void theStrongerItemWinsTheSlot()
	{
		PlayerProfile profile = PlayerProfile.flat(90).toBuilder()
			.itemName(RUNE_KITESHIELD, "Rune kiteshield")
			.itemStats(RUNE_KITESHIELD, stats(SHIELD_SLOT, 0, 0, 0f, -6, -3))
			.itemName(CUSTODIAN_ANTLER_GUARD, "Custodian antler guard")
			.itemStats(CUSTODIAN_ANTLER_GUARD, stats(SHIELD_SLOT, 0, 0, 5f, 30, 25))
			.build();

		assertEquals("Custodian antler guard",
			slot(advisor.adviseCombat(GearCategory.MAGIC, profile, null), EquipSlot.SHIELD)
				.getBestOwnedName());
	}

	@Test
	public void eachStyleRanksOnItsOwnStat()
	{
		int mageWeapon = 900001;
		int meleeWeapon = 900002;

		PlayerProfile profile = PlayerProfile.flat(90).toBuilder()
			.itemName(mageWeapon, "Something magical")
			.itemStats(mageWeapon, stats(WEAPON_SLOT, 0, 0, 20f, 60, 0))
			.itemName(meleeWeapon, "Something heavy")
			.itemStats(meleeWeapon, stats(WEAPON_SLOT, 90, 0, 0f, -20, 0))
			.build();

		assertEquals("magic should rank on magic damage",
			"Something magical",
			slot(advisor.adviseCombat(GearCategory.MAGIC, profile, null), EquipSlot.WEAPON)
				.getBestOwnedName());

		assertEquals("melee should rank on strength",
			"Something heavy",
			slot(advisor.adviseCombat(GearCategory.MELEE, profile, null), EquipSlot.WEAPON)
				.getBestOwnedName());
	}

	@Test
	public void gearBetterThanTheLadderSuppressesTheSuggestion()
	{
		// Whatever the ladder would suggest, what the player owns is better, and telling
		// someone to downgrade is worse than saying nothing.
		PlayerProfile bare = PlayerProfile.flat(90);
		GearSuggestion before = slot(
			advisor.adviseCombat(GearCategory.MELEE, bare, null), EquipSlot.SHIELD);
		assertNotNull("the ladder should suggest something to begin with", before.getNext());

		PlayerProfile geared = bare.toBuilder()
			.itemName(CUSTODIAN_ANTLER_GUARD, "Custodian antler guard")
			.itemStats(CUSTODIAN_ANTLER_GUARD, stats(SHIELD_SLOT, 500, 500, 50f, 500, 500))
			.itemStats(before.getNext().getItemId(), stats(SHIELD_SLOT, 5, 0, 0f, 0, 0))
			.build();

		GearSuggestion shield = slot(
			advisor.adviseCombat(GearCategory.MELEE, geared, null), EquipSlot.SHIELD);

		assertNull("nothing on the ladder beats what they already have", shield.getNext());
	}

	@Test
	public void ownedGearFromADifferentSlotIsNotConsidered()
	{
		PlayerProfile profile = PlayerProfile.flat(90).toBuilder()
			.itemName(CUSTODIAN_ANTLER_GUARD, "Custodian antler guard")
			.itemStats(CUSTODIAN_ANTLER_GUARD, stats(SHIELD_SLOT, 0, 0, 5f, 30, 25))
			.build();

		assertNull("a shield must not fill the weapon slot",
			slot(advisor.adviseCombat(GearCategory.MAGIC, profile, null), EquipSlot.WEAPON)
				.getBestOwnedName());
	}

	@Test
	public void aProfileWithNoStatsBehavesAsBefore()
	{
		// Before a bank has ever been opened there are no stats to rank, and the curated
		// ladder has to keep working on its own.
		GearSuggestion weapon = slot(
			advisor.adviseCombat(GearCategory.MELEE, PlayerProfile.flat(70), null), EquipSlot.WEAPON);

		assertNull(weapon.getBestOwnedName());
		assertNotNull("the ladder should still make a suggestion", weapon.getGoal());
		assertTrue(weapon.getGoal().getTier() > 0);
	}
}
