package com.runebuddy;

import com.runebuddy.data.GearCategory;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Which combat ladder the gear tab opens on. {@link #AUTO} picks the style whose
 * levels the account has actually invested in.
 */
@AllArgsConstructor
@Getter
public enum StylePreference
{
	AUTO("Auto-detect", null),
	MELEE("Melee", GearCategory.MELEE),
	RANGED("Ranged", GearCategory.RANGED),
	MAGIC("Magic", GearCategory.MAGIC);

	private final String label;

	/**
	 * The category this pins to, or null for {@link #AUTO}.
	 */
	@Nullable
	private final GearCategory category;

	@Override
	public String toString()
	{
		return label;
	}
}
