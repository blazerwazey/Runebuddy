package com.runebuddy.engine;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.runebuddy.AccountTypeOverride;
import com.runebuddy.MembershipOverride;
import com.runebuddy.RunebuddyConfig;
import com.runebuddy.data.DataStore;
import com.runebuddy.data.GearItem;
import com.runebuddy.data.Requirements;
import com.runebuddy.data.Skills;
import com.runebuddy.data.TrainingMethod;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

/**
 * Builds {@link PlayerProfile} snapshots from live client state.
 *
 * <p>Every read here must happen on the client thread. The snapshot it produces is
 * immutable and is what crosses to the Swing panel, so the panel never needs the client.
 *
 * <p>The bank is only readable while it is open, so what we see is cached per RuneScape
 * account and reloaded on login. That lets the gear tab say "you already own this" on a
 * fresh session, before the player has been anywhere near a bank.
 */
@Slf4j
@Singleton
public class ProfileTracker
{
	private static final String BANK_SNAPSHOT_KEY = "bankSnapshot";

	private static final Type BANK_SNAPSHOT_TYPE = new TypeToken<Map<Integer, Integer>>()
	{
	}.getType();

	/**
	 * Bank containers can hold many hundreds of stacks. We only care about items the
	 * data files actually mention, plus coins, so the cache stays small enough to sit
	 * in the config file without bloating it.
	 */
	private final Set<Integer> itemsOfInterest = new HashSet<>();

	/**
	 * Only the quests the data files reference get their state read, rather than all
	 * two hundred of them, every tick.
	 */
	private final Set<Quest> questsOfInterest = new LinkedHashSet<>();

	private final Map<Integer, Integer> bankCache = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private boolean bankKnown;
	private String loadedProfileKey;

	/**
	 * Works out which items and quests are worth tracking. Called once when the plugin
	 * starts, after the data files have loaded.
	 */
	public void prime(DataStore data)
	{
		itemsOfInterest.clear();
		questsOfInterest.clear();

		itemsOfInterest.add(ItemID.COINS);

		for (TrainingMethod method : data.getMethods())
		{
			collect(method.getRequirements());
		}

		for (GearItem item : data.getGear())
		{
			itemsOfInterest.add(item.getItemId());
			collect(item.getRequirements());
		}
	}

	private void collect(Requirements requirements)
	{
		itemsOfInterest.addAll(requirements.getRequiredItems());
		questsOfInterest.addAll(requirements.getRequiredQuests());
	}

	/**
	 * Reads the current state of the account.
	 *
	 * <p>Must be called on the client thread.
	 */
	public PlayerProfile snapshot(RunebuddyConfig config)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return PlayerProfile.LOGGED_OUT;
		}

		loadBankCacheIfNeeded();

		PlayerProfile.PlayerProfileBuilder builder = PlayerProfile.builder()
			.loggedIn(true)
			.ironman(resolveIronman(config))
			.members(resolveMembers(config))
			.questPoints(client.getVarpValue(VarPlayerID.QP))
			.bankKnown(bankKnown);

		for (Skill skill : Skills.trainable())
		{
			builder.level(skill, client.getRealSkillLevel(skill));
		}

		for (Quest quest : questsOfInterest)
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				builder.completedQuest(quest);
			}
		}

		Map<Integer, Integer> owned = new HashMap<>(bankCache);
		addContainer(owned, InventoryID.INV);
		addContainer(owned, InventoryID.WORN);
		builder.ownedItems(owned);

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				Item item = equipment.getItem(slot.getSlotIdx());
				if (item != null && item.getId() > 0)
				{
					builder.equipped(slot.getSlotIdx(), item.getId());
				}
			}
		}

		builder.liquidGp(owned.getOrDefault(ItemID.COINS, 0));

		return builder.build();
	}

	/**
	 * Records what is in the bank. Call when the bank container changes.
	 */
	public void onBankChanged(ItemContainer bank)
	{
		if (bank == null)
		{
			return;
		}

		bankCache.clear();
		for (Item item : bank.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0 && itemsOfInterest.contains(item.getId()))
			{
				bankCache.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		bankKnown = true;
		saveBankCache();
	}

	/**
	 * Drops the in-memory bank cache so the next login reloads it for whichever account
	 * logs in.
	 */
	public void reset()
	{
		bankCache.clear();
		bankKnown = false;
		loadedProfileKey = null;
	}

	private void addContainer(Map<Integer, Integer> owned, int containerId)
	{
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				owned.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	private boolean resolveIronman(RunebuddyConfig config)
	{
		AccountTypeOverride override = config.accountTypeOverride();
		if (override == AccountTypeOverride.MAIN)
		{
			return false;
		}

		if (override == AccountTypeOverride.IRONMAN)
		{
			return true;
		}

		// Client#getAccountType() is deprecated in favour of reading the varbit directly.
		// Zero is a normal account; every other value is some flavour of ironman.
		return client.getVarbitValue(VarbitID.IRONMAN) != 0;
	}

	private boolean resolveMembers(RunebuddyConfig config)
	{
		MembershipOverride override = config.membershipOverride();
		if (override == MembershipOverride.FREE_TO_PLAY)
		{
			return false;
		}

		if (override == MembershipOverride.MEMBERS)
		{
			return true;
		}

		return client.getWorldType().contains(WorldType.MEMBERS);
	}

	private void loadBankCacheIfNeeded()
	{
		String profileKey = configManager.getRSProfileKey();
		if (profileKey == null || profileKey.equals(loadedProfileKey))
		{
			return;
		}

		loadedProfileKey = profileKey;
		bankCache.clear();
		bankKnown = false;

		// ConfigManager only knows how to serialise a handful of types (a Set among them,
		// but not a Map), so the snapshot is stored as a JSON string we handle ourselves.
		String stored = configManager.getRSProfileConfiguration(RunebuddyConfig.GROUP, BANK_SNAPSHOT_KEY);
		if (stored == null || stored.isEmpty())
		{
			return;
		}

		try
		{
			Map<Integer, Integer> parsed = gson.fromJson(stored, BANK_SNAPSHOT_TYPE);
			if (parsed != null && !parsed.isEmpty())
			{
				bankCache.putAll(parsed);
				bankKnown = true;
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Runebuddy: discarding unreadable bank snapshot", e);
			configManager.unsetRSProfileConfiguration(RunebuddyConfig.GROUP, BANK_SNAPSHOT_KEY);
		}
	}

	private void saveBankCache()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(RunebuddyConfig.GROUP, BANK_SNAPSHOT_KEY,
			gson.toJson(bankCache, BANK_SNAPSHOT_TYPE));
	}
}
