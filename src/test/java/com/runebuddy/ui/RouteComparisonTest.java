package com.runebuddy.ui;

import com.google.gson.Gson;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.Skills;
import com.runebuddy.engine.EngineSettings;
import com.runebuddy.engine.PlayerProfile;
import com.runebuddy.engine.RecommendationEngine;
import com.runebuddy.engine.TimeEstimator;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RouteComparisonTest
{
	@Test
	public void rendersForEverySkillIncludingOnesWithoutMethods()
	{
		TimeEstimator estimator = new TimeEstimator(new RecommendationEngine(DataStore.load(new Gson())));
		PlayerProfile profile = PlayerProfile.flat(40);

		for (Skill skill : Skills.trainable())
		{
			RouteComparison panel = new RouteComparison(
				estimator.alternatives(skill, profile, 50, EngineSettings.defaults(), null),
				40, 50, target -> { }, false, () -> { });

			assertTrue(skill + " should render at least the header", panel.getComponentCount() >= 2);
		}
	}
}
