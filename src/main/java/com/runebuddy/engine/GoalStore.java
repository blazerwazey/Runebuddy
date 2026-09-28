package com.runebuddy.engine;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.runebuddy.RunebuddyConfig;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

/**
 * The player's goals, saved per RuneScape account.
 *
 * <p>Stored as a JSON string through the RS-profile config, the same way as the bank
 * snapshot, because {@link ConfigManager} does not serialise lists of objects itself.
 * Every method is synchronised: the panel reads and edits goals on the EDT, while
 * another thread may be switching accounts.
 */
@Slf4j
public class GoalStore
{
	/**
	 * Enough to plan a session around; more than this and the Plan tab stops being a
	 * plan and becomes a wishlist.
	 */
	public static final int MAX_GOALS = 8;

	public static final String GOALS_KEY = "goals";

	private static final Type GOAL_LIST = new TypeToken<List<Goal>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Goal> goals = new ArrayList<>();

	/**
	 * The account the cached goals belong to, so a switch reloads them.
	 */
	@Nullable
	private String loadedFor;

	public GoalStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * The current account's goals, oldest first. Empty when logged out.
	 */
	public synchronized List<Goal> goals()
	{
		reloadIfNeeded();
		return Collections.unmodifiableList(new ArrayList<>(goals));
	}

	/**
	 * Adds a goal, replacing any goal with the same key (so a new target for a skill
	 * replaces the old one in place).
	 *
	 * @return false when there is no account to save against, or the list is full
	 */
	public synchronized boolean add(Goal goal)
	{
		reloadIfNeeded();
		if (loadedFor == null || !goal.isWellFormed())
		{
			return false;
		}

		for (int i = 0; i < goals.size(); i++)
		{
			if (goals.get(i).key().equals(goal.key()))
			{
				goals.set(i, goal);
				save();
				return true;
			}
		}

		if (goals.size() >= MAX_GOALS)
		{
			return false;
		}

		goals.add(goal);
		save();
		return true;
	}

	public synchronized void remove(Goal goal)
	{
		reloadIfNeeded();
		if (goals.removeIf(g -> g.key().equals(goal.key())))
		{
			save();
		}
	}

	public synchronized boolean contains(Goal goal)
	{
		reloadIfNeeded();
		for (Goal g : goals)
		{
			if (g.key().equals(goal.key()))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * The target level set for a skill, or null when there is no goal for it.
	 */
	@Nullable
	public synchronized Integer skillTarget(Skill skill)
	{
		reloadIfNeeded();
		for (Goal g : goals)
		{
			if (g.getType() == Goal.Type.SKILL && g.getSkill() == skill)
			{
				return g.getLevel();
			}
		}

		return null;
	}

	public synchronized boolean isFull()
	{
		reloadIfNeeded();
		return goals.size() >= MAX_GOALS;
	}

	private void reloadIfNeeded()
	{
		String profileKey = configManager.getRSProfileKey();
		if (Objects.equals(profileKey, loadedFor))
		{
			return;
		}

		goals.clear();
		loadedFor = profileKey;
		if (profileKey == null)
		{
			return;
		}

		String stored = configManager.getRSProfileConfiguration(RunebuddyConfig.GROUP, GOALS_KEY);
		if (stored == null || stored.isEmpty())
		{
			return;
		}

		try
		{
			List<Goal> parsed = gson.fromJson(stored, GOAL_LIST);
			if (parsed != null)
			{
				for (Goal goal : parsed)
				{
					// A goal with a skill this client no longer knows reads back with a
					// null skill; dropping it beats failing the whole list.
					if (goal != null && goal.isWellFormed() && goals.size() < MAX_GOALS)
					{
						goals.add(goal);
					}
				}
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Runebuddy: discarding unreadable goals", e);
		}
	}

	private void save()
	{
		if (loadedFor == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(RunebuddyConfig.GROUP, GOALS_KEY, gson.toJson(goals, GOAL_LIST));
	}
}
