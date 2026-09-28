package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Describes a data set: which schema it follows and how new it is.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DataManifest
{
	/**
	 * Bumped whenever the data files change shape. A plugin only ever loads data in the
	 * schema it was built for, so an old install never misreads newer data.
	 */
	private int schema;

	/**
	 * Sortable version, such as "2026-09-28" or "2026-09-28.2". Later sorts higher.
	 */
	private String version;

	public boolean isNewerThan(String other)
	{
		return version != null && (other == null || version.compareTo(other) > 0);
	}
}
