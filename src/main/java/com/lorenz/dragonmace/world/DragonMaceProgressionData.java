package com.lorenz.dragonmace.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.lorenz.dragonmace.DragonMaceProgression;
import com.lorenz.dragonmace.DragonMaceRuneSelection;
import com.lorenz.dragonmace.DragonMaceRuneToggles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent, per-world kill progression and rune selection for every player.
 *
 * <p>Stored in the overworld's {@code DimensionDataStorage}, which writes
 * {@code data/dragon_mace_progression.dat} inside the world folder. Keyed by player UUID, so the record
 * survives reconnects, deaths, dimension changes and name changes, and it is never derived from a live
 * entity.
 *
 * <p>Kill counts only ever increase: {@link #recordKill} adds one and never resets, which is what makes
 * rune unlocks permanent. A record is only created when a player actually earns a kill or saves a
 * selection, so a player who never interacts with the system has no entry.
 */
public class DragonMaceProgressionData extends SavedData {
	public static final String DATA_ID = "dragon_mace_progression";

	/** One player's progression. */
	public record Entry(int kills, DragonMaceRuneSelection runes, DragonMaceRuneToggles toggles) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("kills", 0).forGetter(Entry::kills),
				DragonMaceRuneSelection.CODEC.optionalFieldOf("runes", DragonMaceRuneSelection.defaults())
						.forGetter(Entry::runes),
				DragonMaceRuneToggles.CODEC.optionalFieldOf("toggles", DragonMaceRuneToggles.defaults())
						.forGetter(Entry::toggles)
		).apply(instance, Entry::new));

		public Entry {
			kills = Math.max(0, kills);
			runes = DragonMaceRuneSelection.orDefaults(runes);
			toggles = DragonMaceRuneToggles.orDefaults(toggles);
		}

		/** An entry with every rune category enabled. */
		public Entry(int kills, DragonMaceRuneSelection runes) {
			this(kills, runes, DragonMaceRuneToggles.defaults());
		}

		/** The entry of a player who has not earned anything yet. */
		public static Entry fresh() {
			return new Entry(0, DragonMaceRuneSelection.defaults(), DragonMaceRuneToggles.defaults());
		}
	}

	public static final Codec<DragonMaceProgressionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Entry.CODEC)
					.optionalFieldOf("players", Map.<String, Entry>of())
					.forGetter(data -> data.players)
	).apply(instance, DragonMaceProgressionData::new));

	public static final SavedDataType<DragonMaceProgressionData> TYPE =
			new SavedDataType<>(DATA_ID, DragonMaceProgressionData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, Entry> players;

	public DragonMaceProgressionData() {
		this.players = new HashMap<>();
	}

	public DragonMaceProgressionData(Map<String, Entry> players) {
		this.players = new HashMap<>(players == null ? Map.of() : players);
	}

	/** The stored entry of {@code player}, or {@code null} when they have none. */
	public Entry entry(UUID player) {
		return player == null ? null : this.players.get(player.toString());
	}

	/** The kill count of {@code player}; 0 when they have no record. */
	public int kills(UUID player) {
		Entry entry = entry(player);
		return entry == null ? 0 : entry.kills();
	}

	/**
	 * The rune selection of {@code player}.
	 *
	 * <p>Sanitised against their own kill count on read, so a hand-edited save can never leave a player
	 * running a rune they have not unlocked.
	 */
	public DragonMaceRuneSelection runes(UUID player) {
		Entry entry = entry(player);

		if (entry == null) {
			return DragonMaceRuneSelection.defaults();
		}

		return entry.runes().sanitisedAt(entry.kills());
	}

	/**
	 * The rune toggles of {@code player}.
	 *
	 * <p>Never sanitised: a toggle carries no unlock requirement, so it survives any kill count.
	 */
	public DragonMaceRuneToggles toggles(UUID player) {
		Entry entry = entry(player);

		if (entry == null) {
			return DragonMaceRuneToggles.defaults();
		}

		return entry.toggles();
	}

	/**
	 * Replaces the rune toggles of {@code player}, keeping their kills and rune selection.
	 *
	 * @return true when the stored value changed
	 */
	public boolean setToggles(UUID player, DragonMaceRuneToggles toggles) {
		if (player == null) {
			return false;
		}

		Entry current = entry(player);
		int kills = current == null ? 0 : current.kills();
		DragonMaceRuneSelection runes = current == null
				? DragonMaceRuneSelection.defaults()
				: current.runes();
		DragonMaceRuneToggles sanitised = DragonMaceRuneToggles.orDefaults(toggles);

		if (current != null && current.toggles().equals(sanitised)) {
			return false;
		}

		this.players.put(player.toString(), new Entry(kills, runes, sanitised));
		this.setDirty();
		return true;
	}

	/**
	 * Adds one qualifying kill for {@code player}.
	 *
	 * @return the new kill count
	 */
	public int recordKill(UUID player) {
		if (player == null) {
			return 0;
		}

		Entry current = entry(player);
		int kills = (current == null ? 0 : current.kills()) + 1;
		DragonMaceRuneSelection runes = current == null
				? DragonMaceRuneSelection.defaults()
				: current.runes();
		DragonMaceRuneToggles toggles = current == null
				? DragonMaceRuneToggles.defaults()
				: current.toggles();

		this.players.put(player.toString(), new Entry(kills, runes, toggles));
		this.setDirty();
		return kills;
	}

	/** Replaces the rune selection of {@code player}, keeping their kill count and toggles. */
	public boolean setRunes(UUID player, DragonMaceRuneSelection selection) {
		if (player == null) {
			return false;
		}

		Entry current = entry(player);
		int kills = current == null ? 0 : current.kills();
		DragonMaceRuneToggles toggles = current == null
				? DragonMaceRuneToggles.defaults()
				: current.toggles();
		DragonMaceRuneSelection sanitised = DragonMaceRuneSelection.orDefaults(selection).sanitisedAt(kills);

		if (current != null && current.runes().equals(sanitised)) {
			return false;
		}

		this.players.put(player.toString(), new Entry(kills, sanitised, toggles));
		this.setDirty();
		return true;
	}

	/** Number of players with a stored record, used by tests and diagnostics. */
	public int size() {
		return this.players.size();
	}

	public static DragonMaceProgressionData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Flushes the data as soon as the storage thread can, instead of waiting for the next autosave. */
	public static void save(MinecraftServer server) {
		server.overworld().getDataStorage().scheduleSave();
	}

	/** Forces class initialisation of {@link DragonMaceProgression} where a caller only needs the type. */
	static void touch() {
		DragonMaceProgression.class.getName();
	}
}
