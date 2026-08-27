package com.runebuddy.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Loads and indexes the bundled training method and gear data.
 *
 * <p>Everything is read once, validated, and then exposed as unmodifiable collections.
 * A structural problem in the data (an unknown skill, a duplicate id) fails the load
 * loudly rather than silently producing bad advice; softer problems are logged.
 */
@Slf4j
public class DataStore
{
	private static final String METHODS_RESOURCE = "/com/runebuddy/training_methods.json";
	private static final String GEAR_RESOURCE = "/com/runebuddy/gear.json";
	private static final String CONTENT_RESOURCE = "/com/runebuddy/content.json";

	private static final Type METHOD_LIST = new TypeToken<List<TrainingMethod>>()
	{
	}.getType();

	private static final Type GEAR_LIST = new TypeToken<List<GearItem>>()
	{
	}.getType();

	private static final Type CONTENT_LIST = new TypeToken<List<ContentActivity>>()
	{
	}.getType();

	/**
	 * Every method, in data-file order.
	 */
	@Getter
	private final List<TrainingMethod> methods;

	/**
	 * Every gear item, in data-file order.
	 */
	@Getter
	private final List<GearItem> gear;

	/**
	 * Every activity, in data-file order.
	 */
	@Getter
	private final List<ContentActivity> content;

	private final Map<Skill, List<TrainingMethod>> methodsBySkill;
	private final Map<GearCategory, Map<EquipSlot, List<GearItem>>> gearByCategory;
	private final Map<Skill, List<GearItem>> toolsBySkill;

	/**
	 * Non-fatal problems found while loading. Empty on a clean data set.
	 */
	@Getter
	private final List<String> warnings;

	private DataStore(List<TrainingMethod> methods, List<GearItem> gear,
					  List<ContentActivity> content, List<String> warnings)
	{
		this.methods = Collections.unmodifiableList(methods);
		this.gear = Collections.unmodifiableList(gear);
		this.content = Collections.unmodifiableList(content);
		this.warnings = Collections.unmodifiableList(warnings);
		this.methodsBySkill = indexMethods(methods);
		this.gearByCategory = indexGear(gear);
		this.toolsBySkill = indexTools(gear);
	}

	/**
	 * Loads the data files bundled in the jar.
	 *
	 * @throws IllegalStateException if the data cannot be read or does not validate
	 */
	public static DataStore load(Gson gson)
	{
		return load(gson, METHODS_RESOURCE, GEAR_RESOURCE, CONTENT_RESOURCE);
	}

	static DataStore load(Gson gson, String methodsResource, String gearResource,
						  String contentResource)
	{
		List<String> warnings = new ArrayList<>();

		List<TrainingMethod> methods = read(gson, methodsResource, METHOD_LIST);
		List<GearItem> gear = read(gson, gearResource, GEAR_LIST);
		List<ContentActivity> content = read(gson, contentResource, CONTENT_LIST);

		Set<String> methodIds = new HashSet<>();
		for (TrainingMethod method : methods)
		{
			method.resolve(warnings::add);
			if (!methodIds.add(method.getId()))
			{
				throw new IllegalStateException("duplicate training method id: " + method.getId());
			}
		}

		Set<String> gearKeys = new HashSet<>();
		for (GearItem item : gear)
		{
			item.resolve(warnings::add);

			// Tiers order the ladder, so two items cannot share one.
			String tierKey = item.getCategory() + "/" + item.getSlot() + "/"
				+ (item.getToolFor() == null ? "" : item.getToolFor()) + "/" + item.getTier();
			if (!gearKeys.add(tierKey))
			{
				throw new IllegalStateException("duplicate gear tier: " + tierKey + " (" + item.getName() + ")");
			}
		}

		Set<String> contentIds = new HashSet<>();
		for (ContentActivity activity : content)
		{
			activity.resolve(warnings::add);
			if (!contentIds.add(activity.getId()))
			{
				throw new IllegalStateException("duplicate content activity id: " + activity.getId());
			}
		}

		for (String warning : warnings)
		{
			log.warn("Runebuddy data: {}", warning);
		}

		return new DataStore(methods, gear, content, warnings);
	}

	private static <T> List<T> read(Gson gson, String resource, Type type)
	{
		try (InputStream in = DataStore.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing bundled data file: " + resource);
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				List<T> parsed = gson.fromJson(reader, type);
				if (parsed == null)
				{
					throw new IllegalStateException("empty data file: " + resource);
				}

				return parsed;
			}
		}
		catch (IOException | JsonParseException e)
		{
			throw new IllegalStateException("could not read " + resource, e);
		}
	}

	/**
	 * Activities in a category, in data-file order.
	 */
	public List<ContentActivity> contentIn(ContentCategory category)
	{
		List<ContentActivity> matching = new ArrayList<>();
		for (ContentActivity activity : content)
		{
			if (activity.getCategory() == category)
			{
				matching.add(activity);
			}
		}

		return Collections.unmodifiableList(matching);
	}

	/**
	 * Every method that trains the given skill, in data-file order.
	 */
	public List<TrainingMethod> methodsFor(Skill skill)
	{
		return methodsBySkill.getOrDefault(skill, Collections.emptyList());
	}

	/**
	 * The gear ladder for a slot within a category, ordered worst to best.
	 */
	public List<GearItem> ladder(GearCategory category, EquipSlot slot)
	{
		return gearByCategory.getOrDefault(category, Collections.emptyMap())
			.getOrDefault(slot, Collections.emptyList());
	}

	/**
	 * The slots that have any gear defined for a category, in {@link EquipSlot} order.
	 */
	public List<EquipSlot> slotsFor(GearCategory category)
	{
		Map<EquipSlot, List<GearItem>> bySlot = gearByCategory.get(category);
		return bySlot == null ? Collections.emptyList() : new ArrayList<>(bySlot.keySet());
	}

	/**
	 * The tool ladder for a skill, ordered worst to best, or empty if it uses no tools.
	 */
	public List<GearItem> toolsFor(Skill skill)
	{
		return toolsBySkill.getOrDefault(skill, Collections.emptyList());
	}

	/**
	 * Skills that have at least one tool defined, in {@link Skill} order.
	 */
	public List<Skill> skillsWithTools()
	{
		return new ArrayList<>(toolsBySkill.keySet());
	}

	/**
	 * Looks up a gear entry by item id, or null if the data files do not mention it.
	 */
	@Nullable
	public GearItem gearByItemId(int itemId, GearCategory category, EquipSlot slot)
	{
		for (GearItem item : ladder(category, slot))
		{
			if (item.getItemId() == itemId)
			{
				return item;
			}
		}

		return null;
	}

	private static Map<Skill, List<TrainingMethod>> indexMethods(List<TrainingMethod> methods)
	{
		Map<Skill, List<TrainingMethod>> index = new EnumMap<>(Skill.class);
		for (TrainingMethod method : methods)
		{
			index.computeIfAbsent(method.getSkill(), s -> new ArrayList<>()).add(method);
		}

		index.replaceAll((skill, list) -> Collections.unmodifiableList(list));
		return Collections.unmodifiableMap(index);
	}

	private static Map<GearCategory, Map<EquipSlot, List<GearItem>>> indexGear(List<GearItem> gear)
	{
		Map<GearCategory, Map<EquipSlot, List<GearItem>>> index = new EnumMap<>(GearCategory.class);
		for (GearItem item : gear)
		{
			index.computeIfAbsent(item.getCategory(), c -> new EnumMap<>(EquipSlot.class))
				.computeIfAbsent(item.getSlot(), s -> new ArrayList<>())
				.add(item);
		}

		Map<GearCategory, Map<EquipSlot, List<GearItem>>> frozen = new EnumMap<>(GearCategory.class);
		index.forEach((category, bySlot) ->
		{
			Map<EquipSlot, List<GearItem>> frozenSlots = new LinkedHashMap<>();
			bySlot.forEach((slot, items) ->
			{
				items.sort(Comparator.comparingInt(GearItem::getTier));
				frozenSlots.put(slot, Collections.unmodifiableList(items));
			});
			frozen.put(category, Collections.unmodifiableMap(frozenSlots));
		});

		return Collections.unmodifiableMap(frozen);
	}

	private static Map<Skill, List<GearItem>> indexTools(List<GearItem> gear)
	{
		Map<Skill, List<GearItem>> index = new EnumMap<>(Skill.class);
		for (GearItem item : gear)
		{
			if (item.getCategory() == GearCategory.SKILLING)
			{
				index.computeIfAbsent(item.getToolFor(), s -> new ArrayList<>()).add(item);
			}
		}

		index.replaceAll((skill, list) ->
		{
			list.sort(Comparator.comparingInt(GearItem::getTier));
			return Collections.unmodifiableList(list);
		});
		return Collections.unmodifiableMap(index);
	}
}
