package com.runebuddy.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Keeps the data current between plugin releases.
 *
 * <p>The data has lagged the game more than once, and a plugin release is a slow way to
 * fix a wrong XP rate. This fetches the same JSON files from the project's repository,
 * runs them through exactly the same validation as the bundled copy, and only then swaps
 * them in, whole. Anything that fails, at any step, leaves the current data in place.
 *
 * <p>The last good copy is cached as one file, written atomically, so a crash mid-write
 * can never leave a mix of two versions behind.
 *
 * <p>Blocking; call from a background thread, never the client thread or the EDT.
 */
@Slf4j
public class DataUpdater
{
	/**
	 * The data shape this build understands. Must match the manifest's schema exactly.
	 */
	public static final int SCHEMA = 1;

	public static final String BASE_URL =
		"https://raw.githubusercontent.com/blazerwazey/Runebuddy/main/src/main/resources/com/runebuddy/";

	public static final String MANIFEST = "manifest.json";

	/**
	 * The data files, in the order {@link DataStore#fromJson} takes them.
	 */
	public static final List<String> FILES = Collections.unmodifiableList(Arrays.asList(
		"training_methods.json", "gear.json", "content.json", "slayer.json"));

	/**
	 * Far above any real data file, and small enough that a bad response cannot eat
	 * memory.
	 */
	static final int MAX_BYTES = 4 * 1024 * 1024;

	/**
	 * Supplies a file by name, or null when it is not there.
	 */
	public interface Fetcher
	{
		@Nullable
		String fetch(String name) throws IOException;
	}

	private final Gson gson;
	private final Fetcher fetcher;
	private final File cacheFile;
	private final Consumer<DataStore> onUpdate;

	/**
	 * Version of the data in use right now.
	 */
	private String currentVersion;

	public DataUpdater(Gson gson, Fetcher fetcher, File cacheFile, String bundledVersion,
					   Consumer<DataStore> onUpdate)
	{
		this.gson = gson;
		this.fetcher = fetcher;
		this.cacheFile = cacheFile;
		this.currentVersion = bundledVersion;
		this.onUpdate = onUpdate;
	}

	/**
	 * The manifest bundled in the jar.
	 */
	public static DataManifest bundledManifest(Gson gson)
	{
		return gson.fromJson(DataStore.readResource("/com/runebuddy/" + MANIFEST), DataManifest.class);
	}

	/**
	 * Fetches over HTTPS with the client RuneLite provides.
	 */
	public static Fetcher http(OkHttpClient client)
	{
		return name ->
		{
			Request request = new Request.Builder().url(BASE_URL + name).build();
			try (Response response = client.newCall(request).execute())
			{
				ResponseBody body = response.body();
				if (!response.isSuccessful() || body == null)
				{
					return null;
				}

				return readLimited(body.byteStream());
			}
		};
	}

	public synchronized String getCurrentVersion()
	{
		return currentVersion;
	}

	/**
	 * Applies the cached copy if it is newer than what is in use.
	 *
	 * @return true when it was applied
	 */
	public synchronized boolean loadCache()
	{
		if (!cacheFile.isFile())
		{
			return false;
		}

		try
		{
			CachedData cached = gson.fromJson(
				new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8), CachedData.class);
			if (cached == null || cached.manifest == null || cached.files == null)
			{
				return false;
			}

			return apply(cached.manifest, cached.files, false);
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Runebuddy: ignoring unreadable data cache", e);
			return false;
		}
	}

	/**
	 * Checks for newer data and applies it.
	 *
	 * @return true when new data was applied
	 */
	public synchronized boolean refresh()
	{
		try
		{
			String manifestJson = fetcher.fetch(MANIFEST);
			if (manifestJson == null)
			{
				return false;
			}

			DataManifest manifest = gson.fromJson(manifestJson, DataManifest.class);
			if (!isUsable(manifest))
			{
				return false;
			}

			Map<String, String> files = new LinkedHashMap<>();
			for (String name : FILES)
			{
				String body = fetcher.fetch(name);
				if (body == null)
				{
					log.debug("Runebuddy: data update is missing {}", name);
					return false;
				}

				files.put(name, body);
			}

			return apply(manifest, files, true);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Runebuddy: data update failed, keeping the current data", e);
			return false;
		}
	}

	private boolean isUsable(@Nullable DataManifest manifest)
	{
		return manifest != null
			&& manifest.getSchema() == SCHEMA
			&& manifest.isNewerThan(currentVersion);
	}

	/**
	 * Validates a complete data set and, only if every file passes, swaps it in.
	 */
	private boolean apply(DataManifest manifest, Map<String, String> files, boolean writeCache)
	{
		if (!isUsable(manifest))
		{
			return false;
		}

		DataStore store;
		try
		{
			store = DataStore.fromJson(gson,
				files.get(FILES.get(0)), files.get(FILES.get(1)), files.get(FILES.get(2)), files.get(FILES.get(3)));
		}
		catch (RuntimeException e)
		{
			log.warn("Runebuddy: rejected data version {}: {}", manifest.getVersion(), e.getMessage());
			return false;
		}

		if (writeCache)
		{
			writeCache(new CachedData(manifest, files));
		}

		currentVersion = manifest.getVersion();
		log.info("Runebuddy: using data version {}", currentVersion);
		onUpdate.accept(store);
		return true;
	}

	private void writeCache(CachedData data)
	{
		try
		{
			File dir = cacheFile.getParentFile();
			if (dir != null && !dir.isDirectory() && !dir.mkdirs())
			{
				return;
			}

			File temp = new File(dir, cacheFile.getName() + ".tmp");
			try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8))
			{
				gson.toJson(data, writer);
			}

			Files.move(temp.toPath(), cacheFile.toPath(),
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			// Not fatal: the data is still applied, it just is not there for an offline
			// start next time.
			log.debug("Runebuddy: could not cache the data update", e);
		}
	}

	static String readLimited(InputStream in) throws IOException
	{
		byte[] buffer = new byte[8192];
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		int read;
		while ((read = in.read(buffer)) != -1)
		{
			out.write(buffer, 0, read);
			if (out.size() > MAX_BYTES)
			{
				throw new IOException("data file larger than " + MAX_BYTES + " bytes");
			}
		}

		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private static class CachedData
	{
		private final DataManifest manifest;
		private final Map<String, String> files;

		private CachedData(DataManifest manifest, Map<String, String> files)
		{
			this.manifest = manifest;
			this.files = files;
		}
	}
}
