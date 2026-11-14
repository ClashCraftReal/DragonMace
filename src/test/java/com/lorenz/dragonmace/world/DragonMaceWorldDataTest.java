package com.lorenz.dragonmace.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

class DragonMaceWorldDataTest {
	@Test
	void defaultsToNotCrafted() {
		assertFalse(new DragonMaceWorldData().isCrafted());
	}

	@Test
	void markCraftedIsOneWayAndDirtiesTheData() {
		DragonMaceWorldData data = new DragonMaceWorldData();

		assertTrue(data.markCrafted());
		assertTrue(data.isCrafted());
		assertTrue(data.isDirty());

		assertFalse(data.markCrafted(), "second transition must be rejected");
		assertTrue(data.isCrafted());
	}

	@Test
	void craftedFlagSurvivesCodecRoundTrip() {
		DynamicOps<Tag> ops = NbtOps.INSTANCE;

		Tag encoded = DragonMaceWorldData.CODEC.encodeStart(ops, new DragonMaceWorldData(true)).getOrThrow();
		assertTrue(encoded instanceof CompoundTag, "saved data must encode to a compound tag");

		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(ops, encoded).getOrThrow();
		assertTrue(decoded.isCrafted());
	}

	@Test
	void missingFieldDecodesAsNotCrafted() {
		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
		assertFalse(decoded.isCrafted());
	}
}
