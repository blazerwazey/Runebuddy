package com.runebuddy.data;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataUpdaterTest
{
	private static final String BUNDLED = "2026-01-01";

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private final Gson gson = new Gson();
	private final Map<String, String> remote = new HashMap<>();
	private final List<DataStore> applied = new ArrayList<>();
	private File cache;

	@Before
	public void setUp() throws IOException
	{
		cache = new File(temp.newFolder("runebuddy"), "data-cache.json");
		for (String name : DataUpdater.FILES)
		{
			remote.put(name, DataStore.readResource("/com/runebuddy/" + name));
		}
		remote.put(DataUpdater.MANIFEST, manifest(DataUpdater.SCHEMA, "2026-02-01"));
	}

	private static String manifest(int schema, String version)
	{
		return "{\"schema\":" + schema + ",\"version\":\"" + version + "\"}";
	}

	private DataUpdater updater()
	{
		return new DataUpdater(gson, remote::get, cache, BUNDLED, applied::add);
	}

	@Test
	public void newerValidDataIsAppliedAndCached()
	{
		DataUpdater updater = updater();

		assertTrue(updater.refresh());
		assertEquals(1, applied.size());
		assertEquals("2026-02-01", updater.getCurrentVersion());
		assertTrue(cache.isFile());

		assertFalse("the same version twice is not news", updater.refresh());
		assertEquals(1, applied.size());
	}

	@Test
	public void olderDataThanBundledIsIgnored()
	{
		remote.put(DataUpdater.MANIFEST, manifest(DataUpdater.SCHEMA, "2025-12-01"));

		assertFalse(updater().refresh());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void otherSchemasAreNeverLoaded()
	{
		remote.put(DataUpdater.MANIFEST, manifest(DataUpdater.SCHEMA + 1, "2026-02-01"));

		assertFalse(updater().refresh());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void oneBrokenFileRejectsTheWholeSet()
	{
		remote.put("gear.json", "[{\"itemId\": 1, \"name\": \"broken\"}]");

		assertFalse(updater().refresh());
		assertTrue("nothing is ever partly applied", applied.isEmpty());
		assertFalse("and nothing bad is cached", cache.exists());
	}

	@Test
	public void unparseableJsonIsRejected()
	{
		remote.put("slayer.json", "{not json");

		assertFalse(updater().refresh());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void linksOffTheWikiAreRejected()
	{
		remote.put("slayer.json", "[{\"task\":\"Goblins\",\"locations\":[\"Lumbridge\"],"
			+ "\"wikiUrl\":\"https://example.com/phish\"}]");

		assertFalse(updater().refresh());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void aMissingFileMeansNoUpdate()
	{
		remote.remove("content.json");

		assertFalse(updater().refresh());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void noManifestMeansNoUpdate()
	{
		// What happens before the data reaches the default branch: a 404.
		remote.remove(DataUpdater.MANIFEST);

		assertFalse(updater().refresh());
	}

	@Test
	public void fetchErrorsKeepTheCurrentData()
	{
		DataUpdater updater = new DataUpdater(gson, name ->
		{
			throw new IOException("offline");
		}, cache, BUNDLED, applied::add);

		assertFalse(updater.refresh());
		assertEquals(BUNDLED, updater.getCurrentVersion());
	}

	@Test
	public void theCacheServesAnOfflineStart()
	{
		assertTrue(updater().refresh());
		applied.clear();

		DataUpdater offline = new DataUpdater(gson, name -> null, cache, BUNDLED, applied::add);

		assertTrue(offline.loadCache());
		assertEquals(1, applied.size());
		assertEquals("2026-02-01", offline.getCurrentVersion());
	}

	@Test
	public void aCacheOlderThanANewPluginReleaseIsIgnored()
	{
		assertTrue(updater().refresh());
		applied.clear();

		DataUpdater upgraded = new DataUpdater(gson, name -> null, cache, "2026-03-01", applied::add);

		assertFalse(upgraded.loadCache());
		assertTrue(applied.isEmpty());
	}

	@Test
	public void oversizedResponsesAreRefused()
	{
		byte[] huge = new byte[DataUpdater.MAX_BYTES + 1];
		try
		{
			DataUpdater.readLimited(new ByteArrayInputStream(huge));
			fail("should refuse a body over the limit");
		}
		catch (IOException expected)
		{
			// expected
		}
	}

	@Test
	public void bundledManifestMatchesThisBuild()
	{
		DataManifest manifest = DataUpdater.bundledManifest(gson);

		assertEquals(DataUpdater.SCHEMA, manifest.getSchema());
		assertTrue(manifest.getVersion() != null && !manifest.getVersion().isEmpty());
	}
}
