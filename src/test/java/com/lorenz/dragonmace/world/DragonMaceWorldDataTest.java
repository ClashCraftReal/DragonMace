package com.lorenz.dragonmace.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lorenz.dragonmace.DragonMaceOwnership;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

class DragonMaceWorldDataTest {
	private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;

	private static DragonMaceWorldData decode(Tag tag) {
		return DragonMaceWorldData.CODEC.parse(OPS, tag).getOrThrow();
	}

	private static Tag encode(DragonMaceWorldData data) {
		return DragonMaceWorldData.CODEC.encodeStart(OPS, data).getOrThrow();
	}

	@Test
	void defaultsToUncrafted() {
		DragonMaceWorldData data = new DragonMaceWorldData();

		assertEquals(DragonMaceOwnership.UNCRAFTED, data.getState());
		assertFalse(data.isCrafted());
	}

	@Test
	void markCraftedIsOneWayAndDirtiesTheData() {
		DragonMaceWorldData data = new DragonMaceWorldData();

		assertTrue(data.markCrafted());
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
		assertTrue(data.isCrafted());
		assertTrue(data.isDirty());

		assertFalse(data.markCrafted(), "second transition must be rejected");
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
	}

	@Test
	void destructionAndRecoveryTransitions() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();

		assertTrue(data.markMissing());
		assertEquals(DragonMaceOwnership.MISSING, data.getState());
		assertTrue(data.isCrafted(), "a missing mace still counts as crafted");

		assertTrue(data.markPresent());
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
	}

	@Test
	void deletionSuppressesRecovery() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();

		assertTrue(data.markDeleted());
		assertEquals(DragonMaceOwnership.DELETED, data.getState());
		assertFalse(data.markMissing(), "a deleted mace must not become missing");
		assertEquals(DragonMaceOwnership.DELETED, data.getState());

		assertTrue(data.markPresent(), "an administrator may deliberately recover it");
	}

	@Test
	void deletionResetReturnsToUncraftedAndClearsTheOwner() {
		java.util.UUID owner = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		data.setOwner(owner);
		data.markDeleted();

		assertTrue(data.resetToUncrafted());
		assertEquals(DragonMaceOwnership.UNCRAFTED, data.getState());
		assertFalse(data.isCrafted());
		assertNull(data.getOwner());
		assertFalse(data.isOwner(owner));
	}

	@Test
	void resetIsRejectedUnlessTheMaceWasDeleted() {
		java.util.UUID owner = java.util.UUID.randomUUID();
		DragonMaceWorldData present = new DragonMaceWorldData();
		present.markCrafted();
		present.setOwner(owner);

		assertFalse(present.resetToUncrafted(), "a live mace must never be handed back to crafting");
		assertEquals(DragonMaceOwnership.PRESENT, present.getState());
		assertEquals(owner, present.getOwner(), "a rejected reset must keep the owner");

		DragonMaceWorldData missing = new DragonMaceWorldData();
		missing.markCrafted();
		missing.markMissing();

		assertFalse(missing.resetToUncrafted(), "a missing mace must never be handed back to crafting");
		assertEquals(DragonMaceOwnership.MISSING, missing.getState());

		DragonMaceWorldData uncrafted = new DragonMaceWorldData();

		assertFalse(uncrafted.resetToUncrafted());
		assertEquals(DragonMaceOwnership.UNCRAFTED, uncrafted.getState());
	}

	@Test
	void resetStateSurvivesCodecRoundTrip() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		data.setOwner(java.util.UUID.randomUUID());
		data.markDeleted();
		assertTrue(data.resetToUncrafted());

		DragonMaceWorldData decoded = decode(encode(data));

		assertEquals(DragonMaceOwnership.UNCRAFTED, decoded.getState());
		assertFalse(decoded.isCrafted());
		assertNull(decoded.getOwner());
	}

	@Test
	void uncraftedWorldCannotBecomeMissingOrDeleted() {
		DragonMaceWorldData data = new DragonMaceWorldData();

		assertFalse(data.markMissing());
		assertFalse(data.markDeleted());
		assertEquals(DragonMaceOwnership.UNCRAFTED, data.getState());
	}

	@Test
	void everyStateSurvivesCodecRoundTrip() {
		for (DragonMaceOwnership state : DragonMaceOwnership.values()) {
			DragonMaceWorldData data = new DragonMaceWorldData();
			moveTo(data, state);

			Tag encoded = encode(data);
			assertTrue(encoded instanceof CompoundTag, "saved data must encode to a compound tag");

			DragonMaceWorldData decoded = decode(encoded);
			assertEquals(state, decoded.getState(), state + " must survive a round trip");
			assertEquals(data.isCrafted(), decoded.isCrafted());
		}
	}

	@Test
	void respawnCountAndDimensionSurviveCodecRoundTrip() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		data.markMissing();
		data.markPresent();
		data.incrementRespawnCount();
		data.incrementRespawnCount();
		data.setLastDimension("minecraft:the_end");

		DragonMaceWorldData decoded = decode(encode(data));

		assertEquals(2, decoded.getRespawnCount());
		assertEquals("minecraft:the_end", decoded.getLastDimension());
	}

	@Test
	void legacyCraftedFlagDecodesAsPresent() {
		CompoundTag legacy = new CompoundTag();
		legacy.putBoolean("crafted", true);

		DragonMaceWorldData decoded = decode(legacy);

		assertEquals(DragonMaceOwnership.PRESENT, decoded.getState());
		assertTrue(decoded.isCrafted());
		assertEquals(0, decoded.getRespawnCount());
		assertNull(decoded.getLastDimension());
	}

	@Test
	void missingFieldsDecodeAsUncrafted() {
		DragonMaceWorldData decoded = decode(new CompoundTag());

		assertEquals(DragonMaceOwnership.UNCRAFTED, decoded.getState());
		assertFalse(decoded.isCrafted());
	}

	@Test
	void unknownStateFallsBackToTheLegacyFlag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("state", "not_a_state");
		tag.putBoolean("crafted", true);

		assertEquals(DragonMaceOwnership.PRESENT, decode(tag).getState());

		CompoundTag uncrafted = new CompoundTag();
		uncrafted.putString("state", "not_a_state");

		assertEquals(DragonMaceOwnership.UNCRAFTED, decode(uncrafted).getState());
	}

	@Test
	void ownerSurvivesCodecRoundTrip() {
		java.util.UUID owner = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		data.setOwner(owner);

		DragonMaceWorldData decoded = decode(encode(data));

		assertEquals(owner, decoded.getOwner());
		assertTrue(decoded.isOwner(owner));
	}

	@Test
	void ownerIsNullWhenAbsent() {
		DragonMaceWorldData decoded = decode(new CompoundTag());

		assertNull(decoded.getOwner());
		assertFalse(decoded.isOwner(java.util.UUID.randomUUID()));
	}

	@Test
	void malformedOwnerDecodesAsNull() {
		CompoundTag tag = new CompoundTag();
		tag.putString("owner", "not-a-uuid");

		assertNull(decode(tag).getOwner());
	}

	private static void moveTo(DragonMaceWorldData data, DragonMaceOwnership state) {
		switch (state) {
			case UNCRAFTED -> {
			}
			case PRESENT -> data.markCrafted();
			case MISSING -> {
				data.markCrafted();
				data.markMissing();
			}
			case DELETED -> {
				data.markCrafted();
				data.markDeleted();
			}
		}
	}
}
