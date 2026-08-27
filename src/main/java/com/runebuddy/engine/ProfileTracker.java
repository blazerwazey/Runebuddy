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
import net.runelite.api.ItemComposition;
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
import net.runelite.client.game.ItemManager;

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

	@Inject
	private ItemManager itemManager;

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

		itemsOfInterest.add(canonical(ItemID.COINS));

		for (TrainingMethod method : data.getMethods())
		{
			collect(method.getRequirements());
		}

		for (GearItem item : data.getGear())
		{
			itemsOfInterest.add(canonical(item.getItemId()));
			collect(item.getRequirements());
		}
	}

	private void collect(Requirements requirements)
	{
		for (int itemId : requirements.getRequiredItems())
		{
			itemsOfInterest.add(canonical(itemId));
		}

		questsOfInterest.addAll(requirements.getRequiredQuests());
	}

	/**
	 * Folds charges, conditions and noted forms onto one id per item family.
	 */
	private static int canonical(int itemId)
	{
		return PlayerProfile.canonicalItem(itemId);
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
					builder.equipped(slot.getSlotIdx(), canonical(item.getId()));
				}
			}
		}

		builder.liquidGp(owned.getOrDefault(canonical(ItemID.COINS), 0));

		resolveItemDetails(builder);

		return builder.build();
	}

	/**
	 * Resolves the name and price of every item the data files mention.
	 *
	 * <p>Both readings go through the client's item definitions, so they have to happen
	 * here on the client thread rather than lazily from the panel. Doing it there was a
	 * real bug: it trips the client's own thread assertions and takes the whole repaint
	 * down with it.
	 */
	private void resolveItemDetails(PlayerProfile.PlayerProfileBuilder builder)
	{
		for (int itemId : itemsOfInterest)
		{
			try
			{
				ItemComposition composition = itemManager.getItemComposition(itemId);
				if (composition != null && composition.getName() != null)
				{
					builder.itemName(itemId, composition.getName());
				}

				int price = itemManager.getItemPrice(itemId);
				if (price > 0)
				{
					builder.itemPrice(itemId, price);
				}
			}
			catch (RuntimeException e)
			{
				// An id the cache does not know about is not worth losing the snapshot
				// over; the panel copes with a missing name or price.
				log.debug("Runebuddy: could not resolve item {}", itemId, e);
			}
		}
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
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			// Bank an amulet of glory(6) and we still want it recognised against the
			// glory(4) the data file names, so both sides are collapsed to one id.
			int id = canonical(item.getId());
			if (itemsOfInterest.contains(id))
			{
				bankCache.merge(id, item.getQuantity(), Integer::sum);
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
				owned.merge(canonical(item.getId()), item.getQuantity(), Integer::sum);
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
