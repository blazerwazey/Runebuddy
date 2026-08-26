package com.runebuddy.engine;

import lombok.Builder;
import lombok.Value;

/**
 * The knobs the ranking honours, decoupled from the RuneLite config interface so the
 * engine can be exercised without a client.
 */
@Value
@Builder(toBuilder = true)
public class EngineSettings
{
	/**
	 * How much raw experience per hour matters, 0-10.
	 */
	@Builder.Default
	int xpWeight = 6;

	/**
	 * How much the gold cost or profit matters, 0-10.
	 */
	@Builder.Default
	int gpWeight = 4;

	/**
	 * How much low-attention training matters, 0-10.
	 */
	@Builder.Default
	int afkWeight = 3;

	/**
	 * How many ranked methods to return per skill.
	 */
	@Builder.Default
	int methodsPerSkill = 5;

	/**
	 * Hours of supplies the player wants to be able to afford.
	 */
	@Builder.Default
	int budgetHours = 10;

	@Builder.Default
	boolean showUnlocksSoon = true;

	public static EngineSettings defaults()
	{
		return EngineSettings.builder().build();
	}

	/**
	 * Total weight, never zero — if the user zeroes every slider we fall back to
	 * treating the three terms equally rather than dividing by nothing.
	 */
	int totalWeight()
	{
		int total = xpWeight + gpWeight + afkWeight;
		return total > 0 ? total : 3;
	}

	int effectiveXpWeight()
	{
		return xpWeight + gpWeight + afkWeight > 0 ? xpWeight : 1;
	}

	int effectiveGpWeight()
	{
		return xpWeight + gpWeight + afkWeight > 0 ? gpWeight : 1;
	}

	int effectiveAfkWeight()
	{
		return xpWeight + gpWeight + afkWeight > 0 ? afkWeight : 1;
	}
}
