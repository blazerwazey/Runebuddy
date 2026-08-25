package com.runebuddy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RunebuddyConfig.GROUP)
public interface RunebuddyConfig extends Config
{
	String GROUP = "runebuddy";

	@ConfigSection(
		name = "Recommendations",
		description = "How training methods are ranked",
		position = 0
	)
	String recommendationsSection = "recommendations";

	@ConfigSection(
		name = "Account",
		description = "What the advice assumes about your account",
		position = 1
	)
	String accountSection = "account";

	@ConfigSection(
		name = "Gear",
		description = "How gear upgrades are suggested",
		position = 2
	)
	String gearSection = "gear";

	// --- Recommendations -----------------------------------------------------

	@Range(max = 10)
	@ConfigItem(
		keyName = "xpWeight",
		name = "Value XP rate",
		description = "How much you care about raw experience per hour. Raise this to be shown the fastest methods regardless of cost.",
		position = 0,
		section = recommendationsSection
	)
	default int xpWeight()
	{
		return 6;
	}

	@Range(max = 10)
	@ConfigItem(
		keyName = "gpWeight",
		name = "Value gold",
		description = "How much you care about what training costs or earns. Raise this to be steered away from expensive methods.",
		position = 1,
		section = recommendationsSection
	)
	default int gpWeight()
	{
		return 4;
	}

	@Range(max = 10)
	@ConfigItem(
		keyName = "afkWeight",
		name = "Value AFK-ness",
		description = "How much you care about low-attention training. Raise this to prefer methods you can mostly ignore.",
		position = 2,
		section = recommendationsSection
	)
	default int afkWeight()
	{
		return 3;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "methodsPerSkill",
		name = "Methods per skill",
		description = "How many ranked methods to list for each skill.",
		position = 3,
		section = recommendationsSection
	)
	default int methodsPerSkill()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "showUnlocksSoon",
		name = "Show upcoming unlocks",
		description = "List methods you do not qualify for yet but are close to unlocking.",
		position = 4,
		section = recommendationsSection
	)
	default boolean showUnlocksSoon()
	{
		return true;
	}

	// --- Account -------------------------------------------------------------

	@ConfigItem(
		keyName = "accountTypeOverride",
		name = "Account type",
		description = "Ironman accounts are not shown methods that depend on buying supplies.",
		position = 0,
		section = accountSection
	)
	default AccountTypeOverride accountTypeOverride()
	{
		return AccountTypeOverride.AUTO;
	}

	@ConfigItem(
		keyName = "membershipOverride",
		name = "Membership",
		description = "Free-to-play accounts are not shown members-only methods or gear.",
		position = 1,
		section = accountSection
	)
	default MembershipOverride membershipOverride()
	{
		return MembershipOverride.AUTO;
	}

	@Range(min = 1, max = 200)
	@Units(" hours")
	@ConfigItem(
		keyName = "budgetHours",
		name = "Budget",
		description = "How many hours of supplies you want to be able to afford. Methods you cannot sustain for this long are ranked down.",
		position = 2,
		section = accountSection
	)
	default int budgetHours()
	{
		return 10;
	}

	// --- Gear ----------------------------------------------------------------

	@ConfigItem(
		keyName = "preferredStyle",
		name = "Preferred style",
		description = "Which combat style the gear tab opens on.",
		position = 0,
		section = gearSection
	)
	default StylePreference preferredStyle()
	{
		return StylePreference.AUTO;
	}

	@ConfigItem(
		keyName = "showSkillingTools",
		name = "Show skilling tools",
		description = "Include pickaxes, axes, harpoons and similar tools in the gear tab.",
		position = 1,
		section = gearSection
	)
	default boolean showSkillingTools()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useLivePrices",
		name = "Show Grand Exchange prices",
		description = "Look up the current price of suggested upgrades. Turn off if you only care about requirements.",
		position = 2,
		section = gearSection
	)
	default boolean useLivePrices()
	{
		return true;
	}
}
