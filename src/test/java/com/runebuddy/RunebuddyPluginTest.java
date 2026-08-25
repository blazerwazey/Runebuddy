package com.runebuddy;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development launcher. Starts a real RuneLite client with Runebuddy side-loaded so
 * the panel can be exercised against a live account.
 */
public class RunebuddyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RunebuddyPlugin.class);
		RuneLite.main(args);
	}
}
