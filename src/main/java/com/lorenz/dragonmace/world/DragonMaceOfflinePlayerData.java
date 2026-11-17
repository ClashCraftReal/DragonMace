package com.lorenz.dragonmace.world;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.lorenz.dragonmace.DragonMace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Removes the persistent Dragons Aura from a player who is not online.
 *
 * <p>The aura is an infinite {@code minecraft:glowing} effect, and active effects live in the player's
 * own saved data ({@code playerdata/<uuid>.dat}), not in the world record. An offline holder therefore
 * keeps the aura on disk until they log in, so {@code /dm remove} has to edit that file directly.
 *
 * <p>Only the exact aura entry is touched: the effect id, amplifier {@code 0} and the infinite
 * duration must all match, which is the same rule {@code DragonMaceAura.clearStale} applies to a live
 * player. Every other key in the file, including the inventory, is left byte-for-byte as it was, and
 * the rewritten file replaces the original through an atomic move so a crash cannot leave a truncated
 * player file behind.
 */
public final class DragonMaceOfflinePlayerData {
	/** The vanilla key that holds a living entity's active effects. */
	public static final String ACTIVE_EFFECTS_KEY = "active_effects";
	/** The aura effect id as it is written into saved data. */
	public static final String AURA_EFFECT_ID = "minecraft:glowing";
	/** {@code MobEffectInstance.INFINITE_DURATION}. */
	public static final int INFINITE_DURATION = -1;
	/** The vanilla key holding a player's inventory list. */
	public static final String INVENTORY_KEY = "Inventory";
	/** The vanilla key holding a stack's data components. */
	public static final String COMPONENTS_KEY = "components";
	/** The vanilla enchantment component key. */
	public static final String ENCHANTMENTS_KEY = "minecraft:enchantments";
	/** The vanilla stored-enchantment component key, used by enchanted books. */
	public static final String STORED_ENCHANTMENTS_KEY = "minecraft:stored_enchantments";
	/** The vanilla mace item id. */
	public static final String MACE_ITEM_ID = "minecraft:mace";

	/** Outcome of an offline purge, so the caller can log what actually happened. */
	public enum PurgeResult {
		/** The aura was found and removed from the saved data. */
		PURGED,
		/** The file exists but carried no aura. */
		NOTHING_TO_PURGE,
		/** The player has no saved data, e.g. they never joined this world. */
		NO_DATA,
		/** The file could not be read or rewritten; it was left untouched. */
		FAILED
	}

	private DragonMaceOfflinePlayerData() {
	}

	/** The saved-data file of {@code player}, or {@code null} when it cannot be resolved. */
	public static Path playerDataFile(MinecraftServer server, UUID player) {
		if (server == null || player == null) {
			return null;
		}

		return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat");
	}

	/** True when the effect entry is exactly the infinite amplifier-0 glowing aura. */
	public static boolean isAuraEffect(CompoundTag effect) {
		if (effect == null) {
			return false;
		}

		Optional<String> id = effect.getString("id");

		if (id.isEmpty() || !AURA_EFFECT_ID.equals(id.get())) {
			return false;
		}

		if (effect.getIntOr("amplifier", 0) != 0) {
			return false;
		}

		return effect.getIntOr("duration", 0) == INFINITE_DURATION;
	}

	/**
	 * Removes the aura from a player tag in place.
	 *
	 * <p>Every other effect, and every other key, is preserved. The {@code active_effects} key is
	 * dropped entirely once the list is empty, so no empty list is left behind.
	 *
	 * @return {@code true} when the tag changed
	 */
	public static boolean purgeAura(CompoundTag tag) {
		if (tag == null) {
			return false;
		}

		Optional<ListTag> effects = tag.getList(ACTIVE_EFFECTS_KEY);

		if (effects.isEmpty()) {
			return false;
		}

		ListTag list = effects.get();
		boolean changed = false;

		for (int index = list.size() - 1; index >= 0; index--) {
			Optional<CompoundTag> effect = list.getCompound(index);

			if (effect.isPresent() && isAuraEffect(effect.get())) {
				list.remove(index);
				changed = true;
			}
		}

		if (!changed) {
			return false;
		}

		if (list.isEmpty()) {
			tag.remove(ACTIVE_EFFECTS_KEY);
		}

		return true;
	}

	/**
	 * Purges the aura from one saved player file.
	 *
	 * <p>A missing file is reported as {@link PurgeResult#NO_DATA} and never created, so an unknown
	 * player is not given saved data by the removal. A file that cannot be parsed or rewritten is
	 * reported as {@link PurgeResult#FAILED} and left exactly as it was.
	 */
	public static PurgeResult purgeAura(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return PurgeResult.NO_DATA;
		}

		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

			if (!purgeAura(tag)) {
				return PurgeResult.NOTHING_TO_PURGE;
			}

			writeAtomically(tag, file);
			return PurgeResult.PURGED;
		} catch (IOException | RuntimeException e) {
			DragonMace.LOGGER.error("Could not purge the Dragons Aura from {}.", file, e);
			return PurgeResult.FAILED;
		}
	}

	/** Purges the aura from the saved data of an offline player. */
	public static PurgeResult purgeOffline(MinecraftServer server, UUID player) {
		return purgeAura(playerDataFile(server, player));
	}

	/** Purges the aura and strips mace enchantments from an offline player's saved data. */
	public static PurgeResult purgeCurseOffline(MinecraftServer server, UUID player) {
		return purgeCurse(playerDataFile(server, player));
	}

	/**
	 * Purges the aura and strips mace enchantments from one saved player file.
	 *
	 * <p>Used when the curse is removed while the holder is offline, so their stored maces do not keep
	 * enchantments they are no longer entitled to. The file is rewritten atomically and never created.
	 */
	public static PurgeResult purgeCurse(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return PurgeResult.NO_DATA;
		}

		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			boolean changed = purgeAura(tag);
			changed |= stripMaceEnchantments(tag);

			if (!changed) {
				return PurgeResult.NOTHING_TO_PURGE;
			}

			writeAtomically(tag, file);
			return PurgeResult.PURGED;
		} catch (IOException | RuntimeException e) {
			DragonMace.LOGGER.error("Could not purge the curse from {}.", file, e);
			return PurgeResult.FAILED;
		}
	}

	/**
	 * Removes the enchantment components from every mace stack in a saved player tag.
	 *
	 * <p>Only {@code minecraft:mace} entries are touched, and only their enchantment components, so the
	 * rest of the inventory is left exactly as it was.
	 *
	 * @return {@code true} when the tag changed
	 */
	public static boolean stripMaceEnchantments(CompoundTag tag) {
		if (tag == null) {
			return false;
		}

		Optional<ListTag> inventory = tag.getList(INVENTORY_KEY);

		if (inventory.isEmpty()) {
			return false;
		}

		ListTag list = inventory.get();
		boolean changed = false;

		for (int index = 0; index < list.size(); index++) {
			CompoundTag stack = list.getCompoundOrEmpty(index);

			if (!MACE_ITEM_ID.equals(stack.getStringOr("id", ""))) {
				continue;
			}

			CompoundTag components = stack.getCompoundOrEmpty(COMPONENTS_KEY);

			if (components.isEmpty()) {
				continue;
			}

			boolean local = false;

			if (components.contains(ENCHANTMENTS_KEY)) {
				components.remove(ENCHANTMENTS_KEY);
				local = true;
			}

			if (components.contains(STORED_ENCHANTMENTS_KEY)) {
				components.remove(STORED_ENCHANTMENTS_KEY);
				local = true;
			}

			if (local) {
				changed = true;
			}
		}

		return changed;
	}

	/** Counts for a whole-directory purge, so the command can report what happened. */
	public record DirectoryPurge(int purged, int alreadyClean, int failed, int skipped) {
		public int cleaned() {
			return this.purged + this.alreadyClean;
		}
	}

	/** The world's {@code playerdata} directory, or {@code null} when it cannot be resolved. */
	public static Path playerDataDirectory(MinecraftServer server) {
		return server == null ? null : server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
	}

	/**
	 * Purges the aura from every saved player file in {@code directory}.
	 *
	 * <p>Only existing {@code *.dat} files are visited, so a player who never joined is never given
	 * saved data. A file that cannot be read or rewritten is counted as failed and left untouched; the
	 * remaining files are still processed, so one corrupt player file cannot stop the global cleanup.
	 */
	public static DirectoryPurge purgeDirectory(Path directory) {
		if (directory == null || !Files.isDirectory(directory)) {
			return new DirectoryPurge(0, 0, 0, 0);
		}

		int purged = 0;
		int alreadyClean = 0;
		int failed = 0;
		int skipped = 0;

		try (Stream<Path> files = Files.list(directory)) {
			List<Path> candidates = files
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".dat"))
					.sorted()
					.toList();

			for (Path file : candidates) {
				switch (purgeAura(file)) {
					case PURGED -> purged++;
					case NOTHING_TO_PURGE -> alreadyClean++;
					case FAILED -> failed++;
					case NO_DATA -> skipped++;
				}
			}
		} catch (IOException e) {
			DragonMace.LOGGER.error("Could not list the player data directory {}.", directory, e);
			return new DirectoryPurge(purged, alreadyClean, failed + 1, skipped);
		}

		return new DirectoryPurge(purged, alreadyClean, failed, skipped);
	}

	/**
	 * Writes the tag to a sibling temporary file and moves it over the original.
	 *
	 * <p>The move is atomic where the filesystem supports it, so the player file is never observed
	 * half-written. The temporary file is always cleaned up.
	 */
	private static void writeAtomically(CompoundTag tag, Path file) throws IOException {
		Path directory = file.getParent();
		Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");

		try {
			NbtIo.writeCompressed(tag, temporary);

			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
