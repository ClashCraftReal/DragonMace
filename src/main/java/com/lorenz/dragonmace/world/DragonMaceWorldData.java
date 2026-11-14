package com.lorenz.dragonmace.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent, per-world record of whether the Dragon Mace has already been crafted.
 *
 * <p>Stored through {@link net.minecraft.world.level.storage.DimensionDataStorage} of the overworld,
 * which writes {@code data/dragon_mace.dat} inside the world folder. The flag is never cleared, so
 * losing, destroying or voiding the mace does not allow a second one to be crafted.
 */
public class DragonMaceWorldData extends SavedData {
	public static final String DATA_ID = "dragon_mace";

	public static final Codec<DragonMaceWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("crafted").orElse(false).forGetter(DragonMaceWorldData::isCrafted)
	).apply(instance, DragonMaceWorldData::new));

	public static final SavedDataType<DragonMaceWorldData> TYPE =
			new SavedDataType<>(DATA_ID, DragonMaceWorldData::new, CODEC, DataFixTypes.LEVEL);

	private boolean crafted;

	public DragonMaceWorldData() {
	}

	public DragonMaceWorldData(boolean crafted) {
		this.crafted = crafted;
	}

	public boolean isCrafted() {
		return this.crafted;
	}

	/**
	 * Marks the Dragon Mace as crafted and schedules the data for saving.
	 *
	 * @return {@code true} if this call performed the transition, {@code false} if it was already set
	 */
	public boolean markCrafted() {
		if (this.crafted) {
			return false;
		}

		this.crafted = true;
		this.setDirty();
		return true;
	}

	public static DragonMaceWorldData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
