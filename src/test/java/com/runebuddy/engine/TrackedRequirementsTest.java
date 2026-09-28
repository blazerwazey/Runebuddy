package com.runebuddy.engine;

import com.google.gson.Gson;
import com.runebuddy.data.ContentActivity;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.Requirements;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TrackedRequirementsTest
{
	/**
	 * Only tracked quests are ever read from the client. Barrows needs Priest in Peril,
	 * which nothing else in the data asks for, so it used to read as unfinished forever.
	 */
	@Test
	public void everyActivityQuestIsTracked()
	{
		DataStore data = DataStore.load(new Gson());
		Set<Quest> tracked = new HashSet<>();
		List<Requirements> all = ProfileTracker.allRequirements(data);
		for (Requirements requirements : all)
		{
			tracked.addAll(requirements.getRequiredQuests());
		}

		for (ContentActivity activity : data.getContent())
		{
			for (Quest quest : activity.getRequirements().getRequiredQuests())
			{
				assertTrue(activity.getId() + " needs " + quest + ", which is not tracked", tracked.contains(quest));
			}
		}

		assertTrue(tracked.contains(Quest.PRIEST_IN_PERIL));
	}
}
