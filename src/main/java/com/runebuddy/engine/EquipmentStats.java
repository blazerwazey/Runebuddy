package com.runebuddy.engine;

import com.runebuddy.data.EquipSlot;
import com.runebuddy.data.GearCategory;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.EquipmentInventorySlot;

/**
 * What an item is worth wearing, taken from the client's own equipment data.
 *
 * <p>This is the part of the gear picture that does not depend on anything hand-written:
 * RuneLite serves live stats for every item in the game, so an item released after this
 * plugin was written still ranks correctly. Only the aspirational ladder in
 * {@code gear.json} relies on someone having heard of the item.
 */
@Value
public class EquipmentStats
{
	/**
	 * Client-side equipment slot index, matching
	 * {@link EquipmentInventorySlot#getSlotIdx()}.
	 */
	int slotIdx;

	int stabAttack;
	int slashAttack;
	int crushAttack;
	int magicAttack;
	int rangedAttack;

	int stabDefence;
	int slashDefence;
	int crushDefence;
	int magicDefence;
	int rangedDefence;

	/**
	 * Melee strength bonus.
	 */
	int strength;

	/**
	 * Ranged strength bonus.
	 */
	int rangedStrength;

	/**
	 * Magic damage percentage.
	 */
	float magicDamage;

	int prayer;

	/**
	 * The slot this fills in Runebuddy's own terms, or null when the client slot has no
	 * counterpart the gear tab shows.
	 */
	@Nullable
	public EquipSlot slot()
	{
		for (EquipSlot candidate : EquipSlot.values())
		{
			EquipmentInventorySlot clientSlot = candidate.getClientSlot();
			if (clientSlot != null && clientSlot.getSlotIdx() == slotIdx)
			{
				return candidate;
			}
		}

		return null;
	}

	/**
	 * How good this is for a style, as one number to sort by.
	 *
	 * <p>Damage is what people upgrade for, so it leads: strength for melee, ranged
	 * strength for ranged, magic damage for magic. Plenty of slots carry no damage bonus
	 * at all — shields, helmets, capes — so accuracy breaks the tie, and defence breaks
	 * that. Without the fallbacks every defensive slot would score zero and rank
	 * arbitrarily.
	 */
	public int scoreFor(GearCategory category)
	{
		switch (category)
		{
			case RANGED:
				return weigh(rangedStrength, rangedAttack);
			case MAGIC:
				// Magic damage is a percentage, so it needs scaling to sit alongside the
				// flat bonuses rather than being rounded away.
				return weigh(Math.round(magicDamage * 10f), magicAttack);
			case MELEE:
			default:
				return weigh(strength, Math.max(stabAttack, Math.max(slashAttack, crushAttack)));
		}
	}

	private int weigh(int damage, int accuracy)
	{
		return damage * 1000 + accuracy * 10 + defenceTotal();
	}

	private int defenceTotal()
	{
		return stabDefence + slashDefence + crushDefence + magicDefence + rangedDefence;
	}
}
