package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for persistent pickup ownership and the separate temporary item-entity reservation.
 */
class DragonMacePickupTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static DragonMaceWorldData presentWorld() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		assertTrue(data.markCrafted());
		return data;
	}

	@Test
	void firstSuccessfulPickupClaimsTheMace() {
		DragonMaceWorldData data = presentWorld();
		UUID player = UUID.randomUUID();

		assertEquals(DragonMacePickup.Outcome.CLAIMED, DragonMacePickup.recordPickup(data, player));
		assertEquals(player, data.getOwner());
		assertTrue(data.isOwner(player));
	}

	@Test
	void pickupByAnotherPlayerChangesOwnership() {
		DragonMaceWorldData data = presentWorld();
		UUID previous = UUID.randomUUID();
		UUID next = UUID.randomUUID();
		data.setOwner(previous);

		assertEquals(DragonMacePickup.Outcome.CHANGES_HANDS, DragonMacePickup.recordPickup(data, next));
		assertEquals(next, data.getOwner());
		assertFalse(data.isOwner(previous), "the previous owner must no longer be recorded");
	}

	@Test
	void theSameOwnerPickingTheMaceUpAgainDoesNotAnnounceOrChangeAnything() {
		DragonMaceWorldData data = presentWorld();
		UUID owner = UUID.randomUUID();
		data.setOwner(owner);

		assertEquals(DragonMacePickup.Outcome.NONE, DragonMacePickup.recordPickup(data, owner));
		assertEquals(owner, data.getOwner());
	}

	@Test
	void aFailedPickupCannotChangeOwnership() {
		DragonMaceWorldData data = presentWorld();
		UUID owner = UUID.randomUUID();
		data.setOwner(owner);

		assertEquals(DragonMacePickup.Outcome.NONE, DragonMacePickup.recordPickup(data, null));
		assertEquals(owner, data.getOwner());
	}

	@Test
	void aStaleStateIsRepairedByASuccessfulPickup() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		data.markMissing();
		UUID player = UUID.randomUUID();

		assertEquals(DragonMacePickup.Outcome.CLAIMED, DragonMacePickup.recordPickup(data, player));
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
		assertEquals(player, data.getOwner());
	}

	@Test
	void ownershipSurvivesARestart() {
		DragonMaceWorldData data = presentWorld();
		UUID player = UUID.randomUUID();
		DragonMacePickup.recordPickup(data, player);

		DynamicOps<Tag> ops = NbtOps.INSTANCE;
		Tag encoded = DragonMaceWorldData.CODEC.encodeStart(ops, data).getOrThrow();
		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(ops, encoded).getOrThrow();

		assertEquals(player, decoded.getOwner());
		assertEquals(DragonMaceOwnership.PRESENT, decoded.getState());
	}

	@Test
	void aFreeMaceCanBePickedUpByAnyone() {
		UUID player = UUID.randomUUID();

		assertTrue(DragonMacePickup.canPickUp(null, player));
	}

	@Test
	void aReservedMaceOnlyAllowsItsIntendedKiller() {
		UUID killer = UUID.randomUUID();
		UUID otherPlayer = UUID.randomUUID();

		assertTrue(DragonMacePickup.canPickUp(killer, killer));
		assertFalse(DragonMacePickup.canPickUp(killer, otherPlayer));
	}

	@Test
	void theTemporaryReservationAndThePersistentOwnerAreSeparateConcepts() {
		// The reservation is only the ItemEntity target UUID. The persistent owner lives in the saved
		// data and must never be derived from, or overwritten by, clearing a reservation.
		DragonMaceWorldData data = presentWorld();
		UUID killer = UUID.randomUUID();
		data.setOwner(killer);

		assertTrue(DragonMacePickup.canPickUp(killer, killer));
		assertEquals(killer, data.getOwner());
	}
}
