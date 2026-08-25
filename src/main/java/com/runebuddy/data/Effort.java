package com.runebuddy.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * How much attention a training method demands. The weight feeds the AFK term of
 * the ranking score, so a higher weight means "less babysitting".
 */
@AllArgsConstructor
@Getter
public enum Effort
{
	AFK("AFK", 1.0),
	LOW("Low attention", 0.7),
	MEDIUM("Medium attention", 0.45),
	HIGH("Click intensive", 0.25);

	private final String label;
	private final double weight;

	@Override
	public String toString()
	{
		return label;
	}
}
