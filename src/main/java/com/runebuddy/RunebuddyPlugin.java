package com.runebuddy;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Runebuddy",
	description = "Recommends the best training methods and gear upgrades for your account",
	tags = {"training", "skilling", "gear", "progress", "advisor"}
)
public class RunebuddyPlugin extends Plugin
{
	@Inject
	private RunebuddyConfig config;

	@Override
	protected void startUp()
	{
		log.debug("Runebuddy started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Runebuddy stopped");
	}

	@Provides
	RunebuddyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RunebuddyConfig.class);
	}
}
