package com.lorenz.dragonmace.world;

import java.util.Optional;
import java.util.UUID;

import com.lorenz.dragonmace.DragonMaceOwnership;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent, per-world record of the Dragon Mace ownership state and owner.
 *
 * <p>Stored through {@link net.minecraft.world.level.storage.DimensionDataStorage} of the overworld,
 * which writes {@code data/dragon_mace.dat} inside the world folder. Stage 1 only stored a
 * {@code crafted} boolean; that field is still written and read so existing worlds keep working, and
 * a world that already had a mace decodes as {@link DragonMaceOwnership#PRESENT}.
 */
public class DragonMaceWorldData extends SavedData {
	public static final String DATA_ID = "dragon_mace";

	public static final Codec<DragonMaceWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("crafted", false).forGetter(DragonMaceWorldData::isCrafted),
			Codec.STRING.optionalFieldOf("state").forGetter(data -> Optional.of(data.state.id())),
			Codec.INT.optionalFieldOf("respawn_count", 0).forGetter(DragonMaceWorldData::getRespawnCount),
			Codec.STRING.optionalFieldOf("last_dimension").forGetter(data -> Optional.ofNullable(data.lastDimension)),
			Codec.STRING.optionalFieldOf("owner").forGetter(data -> Optional.ofNullable(data.owner)),
			Codec.STRING.optionalFieldOf("curse_owner").forGetter(data -> Optional.ofNullable(data.curseOwner))
	).apply(instance, DragonMaceWorldData::new));

	public static final SavedDataType<DragonMaceWorldData> TYPE =
			new SavedDataType<>(DATA_ID, DragonMaceWorldData::new, CODEC, DataFixTypes.LEVEL);

	private DragonMaceOwnership state;
	private int respawnCount;
	private String lastDimension;
	private String owner;
	private String curseOwner;

	public DragonMaceWorldData() {
		this.state = DragonMaceOwnership.UNCRAFTED;
	}

	public DragonMaceWorldData(boolean crafted, Optional<String> state, int respawnCount,
			Optional<String> lastDimension, Optional<String> owner, Optional<String> curseOwner) {
		// Legacy Stage 1 data has no "state" field; a world that already crafted a mace is PRESENT.
		this.state = state.flatMap(DragonMaceOwnership::byId)
				.orElse(crafted ? DragonMaceOwnership.PRESENT : DragonMaceOwnership.UNCRAFTED);
		this.respawnCount = respawnCount;
		this.lastDimension = lastDimension.orElse(null);
		this.owner = owner.orElse(null);
		this.curseOwner = curseOwner.orElse(null);
	}

	public DragonMaceOwnership getState() {
		return this.state;
	}

	/** True once a mace has ever been crafted, including while it is missing or deleted. */
	public boolean isCrafted() {
		return this.state != DragonMaceOwnership.UNCRAFTED;
	}

	public int getRespawnCount() {
		return this.respawnCount;
	}

	public String getLastDimension() {
		return this.lastDimension;
	}

	public void setLastDimension(String dimension) {
		if (dimension == null ? this.lastDimension == null : dimension.equals(this.lastDimension)) {
			return;
		}

		this.lastDimension = dimension;
		this.setDirty();
	}

	/** The bound owner, or {@code null} when the mace has no owner (uncrafted, missing or deleted). */
	public UUID getOwner() {
		if (this.owner == null) {
			return null;
		}

		try {
			return UUID.fromString(this.owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public void setOwner(UUID owner) {
		String value = owner == null ? null : owner.toString();

		if (value == null ? this.owner == null : value.equals(this.owner)) {
			return;
		}

		this.owner = value;
		this.setDirty();
	}

	public boolean isOwner(UUID player) {
		UUID current = getOwner();
		return current != null && current.equals(player);
	}

	/**
	 * The single player holding the Curse of the Dragons, or {@code null} when the curse is inactive.
	 *
	 * <p>Stored as a UUID string so the curse survives restarts, reconnects, dimension changes and
	 * player name changes, and never depends on the holder keeping a Very Heavy Core.
	 */
	public UUID getCurseOwner() {
		if (this.curseOwner == null) {
			return null;
		}

		try {
			return UUID.fromString(this.curseOwner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public void setCurseOwner(UUID curseOwner) {
		String value = curseOwner == null ? null : curseOwner.toString();

		if (value == null ? this.curseOwner == null : value.equals(this.curseOwner)) {
			return;
		}

		this.curseOwner = value;
		this.setDirty();
	}

	public boolean isCurseOwner(UUID player) {
		UUID current = getCurseOwner();
		return current != null && current.equals(player);
	}

	/**
	 * Moves to {@code target} when the state machine allows that edge.
	 *
	 * @return {@code true} if the transition happened, {@code false} if it was rejected
	 */
	public boolean transitionTo(DragonMaceOwnership target) {
		if (!this.state.canTransitionTo(target)) {
			return false;
		}

		this.state = target;
		this.setDirty();
		return true;
	}

	/** Crafting transition: {@code UNCRAFTED} to {@code PRESENT}. */
	public boolean markCrafted() {
		// Crafting is only ever the first creation: a deleted or missing mace must not be recreated by it.
		if (this.getState() != DragonMaceOwnership.UNCRAFTED) {
			return false;
		}

		return this.transitionTo(DragonMaceOwnership.PRESENT);
	}

	public boolean markMissing() {
		return this.transitionTo(DragonMaceOwnership.MISSING);
	}

	public boolean markDeleted() {
		return this.transitionTo(DragonMaceOwnership.DELETED);
	}

	/**
	 * Administrative reset: {@code DELETED} back to {@code UNCRAFTED} with the previous owner cleared.
	 *
	 * <p>Only a deleted world may be reset, so a live or missing mace can never be handed back to
	 * crafting. The owner is cleared only after the transition succeeded, which keeps the two fields
	 * consistent even if the state machine rejects the reset.
	 *
	 * @return {@code true} when the world is uncrafted again and may craft a new mace
	 */
	public boolean resetToUncrafted() {
		if (!this.transitionTo(DragonMaceOwnership.UNCRAFTED)) {
			return false;
		}

		this.setOwner(null);
		return true;
	}

	public boolean markPresent() {
		return this.transitionTo(DragonMaceOwnership.PRESENT);
	}

	public void incrementRespawnCount() {
		this.respawnCount++;
		this.setDirty();
	}

	public static DragonMaceWorldData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * Flushes the data to disk as soon as the storage thread can, instead of waiting for the next
	 * autosave, so a crash right after a state change cannot resurrect an older state.
	 */
	public static void save(MinecraftServer server) {
		server.overworld().getDataStorage().scheduleSave();
	}
}
