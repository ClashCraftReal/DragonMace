package com.lorenz.dragonmace;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Small JSON configuration for the Dragon Mace.
 *
 * <p>Holds the respawn location and the global Draconic Mace enchantment settings. Only the keys this
 * class owns are read or written: saving re-reads the existing object and replaces those keys, so any
 * unrelated setting in the same file is preserved.
 *
 * <p>A missing, unreadable or invalid value falls back to its default rather than failing the load.
 */
public final class DragonMaceConfig {
	public static final String FILE_NAME = "dragon-mace.json";
	public static final String RESPAWN_LOCATION_KEY = "respawnLocation";
	public static final String WIND_BURST_KEY = "windBurstLevel";
	public static final String MAIN_ENCHANTMENT_KEY = "mainEnchantment";
	public static final String LAST_APPLY_KEY = "lastApplyEpochMillis";
	public static final RespawnLocation DEFAULT_RESPAWN_LOCATION = RespawnLocation.END_FOUNTAIN;
	public static final int DEFAULT_WIND_BURST_LEVEL = 3;
	public static final MainEnchantment DEFAULT_MAIN_ENCHANTMENT = MainEnchantment.DENSITY;

	/** Server-wide cooldown between two successful applies. */
	public static final long APPLY_COOLDOWN_MILLIS = 120_000L;

	/** Highest selectable Wind Burst level; level 0 disables it. */
	public static final int MAX_WIND_BURST_LEVEL = 3;

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

	/**
	 * The one main enchantment that may be selected.
	 *
	 * <p>Exactly one is always active, so the two are mutually exclusive by construction rather than by
	 * a validation rule.
	 */
	public enum MainEnchantment {
		BREACH("breach", 4),
		DENSITY("density", 5);

		private final String id;
		private final int level;

		MainEnchantment(String id, int level) {
			this.id = id;
			this.level = level;
		}

		public String id() {
			return this.id;
		}

		public int level() {
			return this.level;
		}

		public static Optional<MainEnchantment> byId(String id) {
			for (MainEnchantment enchantment : values()) {
				if (enchantment.id.equals(id)) {
					return Optional.of(enchantment);
				}
			}

			return Optional.empty();
		}
	}

	private final RespawnLocation respawnLocation;
	private final int windBurstLevel;
	private final MainEnchantment mainEnchantment;
	private final long lastApplyEpochMillis;

	public DragonMaceConfig(RespawnLocation respawnLocation) {
		this(respawnLocation, DEFAULT_WIND_BURST_LEVEL, DEFAULT_MAIN_ENCHANTMENT, 0L);
	}

	public DragonMaceConfig(RespawnLocation respawnLocation, int windBurstLevel,
			MainEnchantment mainEnchantment, long lastApplyEpochMillis) {
		this.respawnLocation = respawnLocation == null ? DEFAULT_RESPAWN_LOCATION : respawnLocation;
		this.windBurstLevel = clampWindBurst(windBurstLevel);
		this.mainEnchantment = mainEnchantment == null ? DEFAULT_MAIN_ENCHANTMENT : mainEnchantment;
		this.lastApplyEpochMillis = Math.max(0L, lastApplyEpochMillis);
	}

	/** Clamps a Wind Burst level into the selectable 0..3 range. */
	public static int clampWindBurst(int level) {
		return Math.max(0, Math.min(MAX_WIND_BURST_LEVEL, level));
	}

	public RespawnLocation respawnLocation() {
		return this.respawnLocation;
	}

	public int windBurstLevel() {
		return this.windBurstLevel;
	}

	public MainEnchantment mainEnchantment() {
		return this.mainEnchantment;
	}

	public long lastApplyEpochMillis() {
		return this.lastApplyEpochMillis;
	}

	/** The same settings with a different Wind Burst level, clamped into range. */
	public DragonMaceConfig withWindBurstLevel(int level) {
		return new DragonMaceConfig(this.respawnLocation, level, this.mainEnchantment,
				this.lastApplyEpochMillis);
	}

	/** The same settings with a different main enchantment. */
	public DragonMaceConfig withMainEnchantment(MainEnchantment enchantment) {
		return new DragonMaceConfig(this.respawnLocation, this.windBurstLevel, enchantment,
				this.lastApplyEpochMillis);
	}

	/** The same settings stamped with an apply time. */
	public DragonMaceConfig withLastApply(long epochMillis) {
		return new DragonMaceConfig(this.respawnLocation, this.windBurstLevel, this.mainEnchantment,
				epochMillis);
	}

	/** Milliseconds still left on the server-wide apply cooldown at {@code now}. */
	public long remainingCooldownMillis(long now) {
		long elapsed = now - this.lastApplyEpochMillis;

		if (elapsed < 0) {
			return APPLY_COOLDOWN_MILLIS;
		}

		return Math.max(0L, APPLY_COOLDOWN_MILLIS - elapsed);
	}

	/** True when an apply is allowed at {@code now}. */
	public boolean canApply(long now) {
		return remainingCooldownMillis(now) == 0L;
	}

	public static DragonMaceConfig defaults() {
		return new DragonMaceConfig(DEFAULT_RESPAWN_LOCATION);
	}

	/** Parses the configuration text, falling back to defaults per key on any problem. */
	public static DragonMaceConfig parse(String json) {
		if (json == null || json.isBlank()) {
			return defaults();
		}

		try {
			JsonElement root = JsonParser.parseString(json);

			if (!root.isJsonObject()) {
				DragonMace.LOGGER.warn("{} is not a JSON object; using defaults", FILE_NAME);
				return defaults();
			}

			JsonObject object = root.getAsJsonObject();
			return new DragonMaceConfig(readRespawnLocation(object), readWindBurst(object),
					readMainEnchantment(object), readLastApply(object));
		} catch (RuntimeException e) {
			DragonMace.LOGGER.warn("Could not read {}; using defaults", FILE_NAME, e);
			return defaults();
		}
	}

	private static RespawnLocation readRespawnLocation(JsonObject object) {
		JsonElement value = object.get(RESPAWN_LOCATION_KEY);

		if (value == null || !value.isJsonPrimitive()) {
			return DEFAULT_RESPAWN_LOCATION;
		}

		Optional<RespawnLocation> location = RespawnLocation.byId(value.getAsString());

		if (location.isEmpty()) {
			DragonMace.LOGGER.warn("Unknown {} '{}'; using default {}", RESPAWN_LOCATION_KEY,
					value.getAsString(), DEFAULT_RESPAWN_LOCATION.id());
			return DEFAULT_RESPAWN_LOCATION;
		}

		return location.get();
	}

	private static int readWindBurst(JsonObject object) {
		JsonElement value = object.get(WIND_BURST_KEY);

		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return DEFAULT_WIND_BURST_LEVEL;
		}

		return clampWindBurst(value.getAsInt());
	}

	private static MainEnchantment readMainEnchantment(JsonObject object) {
		JsonElement value = object.get(MAIN_ENCHANTMENT_KEY);

		if (value == null || !value.isJsonPrimitive()) {
			return DEFAULT_MAIN_ENCHANTMENT;
		}

		Optional<MainEnchantment> enchantment = MainEnchantment.byId(value.getAsString());

		if (enchantment.isEmpty()) {
			DragonMace.LOGGER.warn("Unknown {} '{}'; using default {}", MAIN_ENCHANTMENT_KEY,
					value.getAsString(), DEFAULT_MAIN_ENCHANTMENT.id());
			return DEFAULT_MAIN_ENCHANTMENT;
		}

		return enchantment.get();
	}

	private static long readLastApply(JsonObject object) {
		JsonElement value = object.get(LAST_APPLY_KEY);

		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return 0L;
		}

		return Math.max(0L, value.getAsLong());
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Loads the configuration once per server process. */
	public static DragonMaceConfig get() {
		if (cached == null) {
			try {
				cached = load();
			} catch (RuntimeException | LinkageError e) {
				// No Fabric loader, no config directory, or an unreadable file: defaults are correct.
				DragonMace.LOGGER.warn("Could not load {}; using defaults", FILE_NAME, e);
				cached = defaults();
			}
		}

		return cached;
	}

	public static void reload() {
		cached = null;
	}

	/** Replaces the in-memory configuration, so later reads see it immediately. */
	public static void set(DragonMaceConfig config) {
		cached = config == null ? defaults() : config;
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
			DragonMace.LOGGER.warn("Could not load {}; using defaults", FILE_NAME, e);
			return defaults();
		}
	}

	/**
	 * Writes the configuration, preserving every key this class does not own.
	 *
	 * <p>The existing file is re-read and only the four owned keys are replaced, so an unrelated
	 * setting added by hand survives a save.
	 *
	 * @return true when the file was written
	 */
	public static boolean save(DragonMaceConfig config) {
		DragonMaceConfig value = config == null ? defaults() : config;
		Path path = path();

		try {
			Files.createDirectories(path.getParent());
			JsonObject object = readExistingObject(path);

			object.addProperty(RESPAWN_LOCATION_KEY, value.respawnLocation().id());
			object.addProperty(WIND_BURST_KEY, value.windBurstLevel());
			object.addProperty(MAIN_ENCHANTMENT_KEY, value.mainEnchantment().id());
			object.addProperty(LAST_APPLY_KEY, value.lastApplyEpochMillis());

			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(object)
					+ "\n", StandardCharsets.UTF_8);
			cached = value;
			return true;
		} catch (IOException | RuntimeException e) {
			DragonMace.LOGGER.error("Could not save {}", FILE_NAME, e);
			return false;
		}
	}

	private static JsonObject readExistingObject(Path path) {
		if (Files.notExists(path)) {
			return new JsonObject();
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseString(readerToString(reader));
			return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
		} catch (IOException | RuntimeException e) {
			DragonMace.LOGGER.warn("Could not re-read {}; rewriting it from scratch", FILE_NAME, e);
			return new JsonObject();
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
		return "{\n  \"" + RESPAWN_LOCATION_KEY + "\": \"" + DEFAULT_RESPAWN_LOCATION.id() + "\",\n  \""
				+ WIND_BURST_KEY + "\": " + DEFAULT_WIND_BURST_LEVEL + ",\n  \"" + MAIN_ENCHANTMENT_KEY
				+ "\": \"" + DEFAULT_MAIN_ENCHANTMENT.id() + "\",\n  \"" + LAST_APPLY_KEY + "\": 0\n}\n";
	}
}
