package com.lorenz.dragonmace;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Small JSON configuration for the Dragon Mace respawn location.
 *
 * <p>Only the {@code respawnLocation} key is read or written, so unrelated settings in the same file
 * are never touched. A missing, unreadable or invalid value falls back to
 * {@link RespawnLocation#END_FOUNTAIN}.
 */
public final class DragonMaceConfig {
	public static final String FILE_NAME = "dragon-mace.json";
	public static final String RESPAWN_LOCATION_KEY = "respawnLocation";
	public static final RespawnLocation DEFAULT_RESPAWN_LOCATION = RespawnLocation.END_FOUNTAIN;

	private static DragonMaceConfig cached;

	public enum RespawnLocation {
		END_FOUNTAIN("end_fountain"),
		WORLD_SPAWN("world_spawn");

		private final String id;

		RespawnLocation(String id) {
			this.id = id;
		}

		public String id() {
			return this.id;
		}

		public static Optional<RespawnLocation> byId(String id) {
			for (RespawnLocation location : values()) {
				if (location.id.equals(id)) {
					return Optional.of(location);
				}
			}

			return Optional.empty();
		}
	}

	private final RespawnLocation respawnLocation;

	public DragonMaceConfig(RespawnLocation respawnLocation) {
		this.respawnLocation = respawnLocation;
	}

	public RespawnLocation respawnLocation() {
		return this.respawnLocation;
	}

	public static DragonMaceConfig defaults() {
		return new DragonMaceConfig(DEFAULT_RESPAWN_LOCATION);
	}

	/** Parses the configuration text, falling back to the default on any problem. */
	public static DragonMaceConfig parse(String json) {
		if (json == null || json.isBlank()) {
			return defaults();
		}

		try {
			JsonElement root = JsonParser.parseString(json);

			if (!root.isJsonObject()) {
				DragonMace.LOGGER.warn("{} is not a JSON object; using default respawn location {}",
						FILE_NAME, DEFAULT_RESPAWN_LOCATION.id());
				return defaults();
			}

			JsonObject object = root.getAsJsonObject();
			JsonElement value = object.get(RESPAWN_LOCATION_KEY);

			if (value == null || !value.isJsonPrimitive()) {
				return defaults();
			}

			Optional<RespawnLocation> location = RespawnLocation.byId(value.getAsString());

			if (location.isEmpty()) {
				DragonMace.LOGGER.warn("Unknown {} '{}'; using default {}",
						RESPAWN_LOCATION_KEY, value.getAsString(), DEFAULT_RESPAWN_LOCATION.id());
				return defaults();
			}

			return new DragonMaceConfig(location.get());
		} catch (RuntimeException e) {
			DragonMace.LOGGER.warn("Could not read {}; using default respawn location {}",
					FILE_NAME, DEFAULT_RESPAWN_LOCATION.id(), e);
			return defaults();
		}
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Loads the configuration once per server process. */
	public static DragonMaceConfig get() {
		if (cached == null) {
			cached = load();
		}

		return cached;
	}

	public static void reload() {
		cached = null;
	}

	public static DragonMaceConfig load() {
		Path path = path();

		try {
			if (Files.notExists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, defaultJson(), StandardCharsets.UTF_8);
				return defaults();
			}

			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				return parse(readerToString(reader));
			}
		} catch (IOException e) {
			DragonMace.LOGGER.warn("Could not load {}; using default respawn location {}",
					FILE_NAME, DEFAULT_RESPAWN_LOCATION.id(), e);
			return defaults();
		}
	}

	private static String readerToString(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[1024];
		int read;

		while ((read = reader.read(buffer)) != -1) {
			builder.append(buffer, 0, read);
		}

		return builder.toString();
	}

	public static String defaultJson() {
		return "{\n  \"" + RESPAWN_LOCATION_KEY + "\": \"" + DEFAULT_RESPAWN_LOCATION.id() + "\"\n}\n";
	}
}
