package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Equipment slots, in the order the gear tab lists them. Mirrors
 * {@link EquipmentInventorySlot} so a worn item can be matched back to a slot,
 * with the addition of {@link #TOOL} for held skilling tools that we want to
 * rank separately from combat weapons.
 */
@AllArgsConstructor
@Getter
public enum EquipSlot
{
	HEAD("Head", EquipmentInventorySlot.HEAD),
	CAPE("Cape", EquipmentInventorySlot.CAPE),
	NECK("Neck", EquipmentInventorySlot.AMULET),
	AMMO("Ammo", EquipmentInventorySlot.AMMO),
	WEAPON("Weapon", EquipmentInventorySlot.WEAPON),
	BODY("Body", EquipmentInventorySlot.BODY),
	SHIELD("Shield", EquipmentInventorySlot.SHIELD),
	LEGS("Legs", EquipmentInventorySlot.LEGS),
	HANDS("Hands", EquipmentInventorySlot.GLOVES),
	FEET("Feet", EquipmentInventorySlot.BOOTS),
	RING("Ring", EquipmentInventorySlot.RING),
	TOOL("Tool", null);

	private final String label;

	/**
	 * The client-side slot this maps to, or null for {@link #TOOL}, which has no
	 * dedicated equipment slot of its own.
	 */
	private final EquipmentInventorySlot clientSlot;

	@Override
	public String toString()
	{
		return label;
	}
}
