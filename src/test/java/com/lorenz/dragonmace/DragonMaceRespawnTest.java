package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceRespawnTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static ItemStack dragonMace() {
		return DragonMaceRecipes.createDragonMaceStack();
	}

	@Test
	void pickupIsNotDestruction() {
		// Vanilla empties the stack before discarding on a successful pickup.
		assertFalse(DragonMaceRespawn.isDestruction(RemovalReason.DISCARDED, ItemStack.EMPTY));
		assertFalse(DragonMaceRespawn.isDestruction(RemovalReason.KILLED, ItemStack.EMPTY));
	}

	@Test
	void voidDespawnAndDamageAreDestruction() {
		assertTrue(DragonMaceRespawn.isDestruction(RemovalReason.DISCARDED, dragonMace()));
		assertTrue(DragonMaceRespawn.isDestruction(RemovalReason.KILLED, dragonMace()));
	}

	@Test
	void unloadingAndDimensionChangesAreNotDestruction() {
		assertFalse(DragonMaceRespawn.isDestruction(RemovalReason.UNLOADED_TO_CHUNK, dragonMace()));
		assertFalse(DragonMaceRespawn.isDestruction(RemovalReason.UNLOADED_WITH_PLAYER, dragonMace()));
		assertFalse(DragonMaceRespawn.isDestruction(RemovalReason.CHANGED_DIMENSION, dragonMace()));
	}

	@Test
	void ordinaryMacesAreNeverTracked() {
		assertFalse(DragonMaceIdentity.isDragonMace(new ItemStack(Items.MACE)));
	}

	@Test
	void voidDestructionMarksTheMaceMissingAndSurvivesARestart() {
		DragonMaceWorldData data = stateOf(DragonMaceOwnership.PRESENT);
		UUID owner = UUID.randomUUID();
		data.setOwner(owner);

		assertTrue(DragonMaceRespawn.markVoidDestroyed(data));
		assertEquals(DragonMaceOwnership.MISSING, data.getState());

		DynamicOps<Tag> ops = NbtOps.INSTANCE;
		Tag encoded = DragonMaceWorldData.CODEC.encodeStart(ops, data).getOrThrow();
		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(ops, encoded).getOrThrow();

		assertEquals(DragonMaceOwnership.MISSING, decoded.getState(), "the missing state must be saved");
		assertEquals(owner, decoded.getOwner(), "void destruction must not silently rewrite the owner");
	}

	@Test
	void voidDestructionIsIgnoredWhenNoLiveMaceIsSaved() {
		assertFalse(DragonMaceRespawn.markVoidDestroyed(null));
		assertFalse(DragonMaceRespawn.markVoidDestroyed(stateOf(DragonMaceOwnership.UNCRAFTED)));
		assertFalse(DragonMaceRespawn.markVoidDestroyed(stateOf(DragonMaceOwnership.MISSING)));
		assertFalse(DragonMaceRespawn.markVoidDestroyed(stateOf(DragonMaceOwnership.DELETED)));
	}

	@Test
	void aMissingMaceWaitsForAnOperatorInsteadOfRespawningAutomatically() {
		assertEquals(DragonMaceRespawn.MissingAction.AWAIT_OPERATOR,
				DragonMaceRespawn.missingAction(DragonMaceOwnership.MISSING, false));
	}

	@Test
	void aMissingMaceThatStillExistsIsAdoptedInsteadOfDuplicated() {
		assertEquals(DragonMaceRespawn.MissingAction.ADOPT_EXISTING,
				DragonMaceRespawn.missingAction(DragonMaceOwnership.MISSING, true));
	}

	@Test
	void theAutomaticTickNeverHandlesAnyOtherState() {
		for (DragonMaceOwnership state : new DragonMaceOwnership[] {
				DragonMaceOwnership.UNCRAFTED, DragonMaceOwnership.PRESENT, DragonMaceOwnership.DELETED }) {
			assertEquals(DragonMaceRespawn.MissingAction.NONE,
					DragonMaceRespawn.missingAction(state, false), state + " must not trigger a recovery");
			assertEquals(DragonMaceRespawn.MissingAction.NONE,
					DragonMaceRespawn.missingAction(state, true), state + " must not trigger a recovery");
		}
	}

	@Test
	void endFountainPositionIsCentredAndAboveTheSurface() {
		BlockPos pos = DragonMaceRespawn.computeSpawnPosition(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				new BlockPos(0, 0, 0), 65, new BlockPos(12, 0, -34), 70);

		assertEquals(new BlockPos(0, 66, 0), pos);
	}

	@Test
	void worldSpawnPositionUsesTheOverworldSpawnColumn() {
		BlockPos pos = DragonMaceRespawn.computeSpawnPosition(DragonMaceConfig.RespawnLocation.WORLD_SPAWN,
				new BlockPos(0, 0, 0), 65, new BlockPos(12, 0, -34), 70);

		assertEquals(new BlockPos(12, 71, -34), pos);
	}

	@Test
	void freshWorldSpawnsANewMace() {
		assertEquals(DragonMaceRespawn.SpawnDecision.SPAWN,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.UNCRAFTED, false));
	}

	@Test
	void duplicateSpawnIsRejectedWhenTheMaceIsLoaded() {
		assertEquals(DragonMaceRespawn.SpawnDecision.ALREADY_PRESENT,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.PRESENT, true));
	}

	@Test
	void presentStateWithoutALoadedMaceIsReportedInsteadOfSpawned() {
		// The mace may sit in an unloaded chunk, so a missing scan is never treated as proof of absence.
		assertEquals(DragonMaceRespawn.SpawnDecision.STATE_SAYS_PRESENT_BUT_NOT_FOUND,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.PRESENT, false));
	}

	@Test
	void aFoundMaceIsAdoptedInsteadOfDuplicated() {
		assertEquals(DragonMaceRespawn.SpawnDecision.FOUND_EXISTING,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.UNCRAFTED, true));
		assertEquals(DragonMaceRespawn.SpawnDecision.FOUND_EXISTING,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.MISSING, true));
		assertEquals(DragonMaceRespawn.SpawnDecision.FOUND_EXISTING,
				DragonMaceRespawn.decideSpawn(DragonMaceOwnership.DELETED, true));
	}

	@Test
	void failedInsertionLeavesEveryStateUntouched() {
		for (DragonMaceOwnership state : DragonMaceOwnership.values()) {
			DragonMaceWorldData data = stateOf(state);
			UUID owner = UUID.randomUUID();
			data.setOwner(owner);

			assertFalse(DragonMaceRespawn.commitSpawn(data, false),
					"a failed insertion must not commit " + state);
			assertEquals(state, data.getState(), state + " must survive a failed insertion");
			assertEquals(owner, data.getOwner(), state + " must keep its owner after a failed insertion");
		}
	}

	@Test
	void successfulInsertionCommitsPresent() {
		DragonMaceWorldData data = stateOf(DragonMaceOwnership.MISSING);

		assertTrue(DragonMaceRespawn.commitSpawn(data, true));
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
	}

	@Test
	void successfulSpawnClearsTheOwnerUntilTheFirstPickup() {
		DragonMaceWorldData data = stateOf(DragonMaceOwnership.MISSING);
		data.setOwner(UUID.randomUUID());

		assertTrue(DragonMaceRespawn.commitSpawn(data, true));
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
		assertNull(data.getOwner(), "an operator respawn must not assign an owner");
	}

	@Test
	void successfulDeletionResetsTheWorldToUncrafted() {
		DragonMaceWorldData data = stateOf(DragonMaceOwnership.DELETED);
		data.setOwner(java.util.UUID.randomUUID());

		assertTrue(DragonMaceRespawn.completeDeletion(data, 1));
		assertEquals(DragonMaceOwnership.UNCRAFTED, data.getState());
		assertNull(data.getOwner());
	}

	@Test
	void failedDeletionKeepsCraftingBlocked() {
		DragonMaceWorldData data = stateOf(DragonMaceOwnership.DELETED);
		java.util.UUID owner = java.util.UUID.randomUUID();
		data.setOwner(owner);

		assertFalse(DragonMaceRespawn.completeDeletion(data, 0),
				"a deletion that removed nothing must not enable crafting");
		assertEquals(DragonMaceOwnership.DELETED, data.getState());
		assertEquals(owner, data.getOwner(), "a failed deletion must keep the owner");
		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void deletionResetIsRejectedForStatesThatStillHoldAMace() {
		for (DragonMaceOwnership state : new DragonMaceOwnership[] {
				DragonMaceOwnership.UNCRAFTED, DragonMaceOwnership.PRESENT, DragonMaceOwnership.MISSING }) {
			DragonMaceWorldData data = stateOf(state);

			assertFalse(DragonMaceRespawn.completeDeletion(data, 1), state + " must not be reset");
			assertEquals(state, data.getState(), state + " must survive a refused reset");
		}
	}

	@Test
	void reconciliationAdoptsAMaceTheStateDidNotKnowAbout() {
		assertEquals(DragonMaceRespawn.Reconcile.ADOPT_EXISTING,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.UNCRAFTED, true));
	}

	@Test
	void reconciliationWarnsWithoutDemoting() {
		assertEquals(DragonMaceRespawn.Reconcile.PRESENT_NOT_FOUND,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.PRESENT, false));
	}

	@Test
	void reconciliationIgnoresAgreeingStates() {
		assertEquals(DragonMaceRespawn.Reconcile.NONE,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.PRESENT, true));
		assertEquals(DragonMaceRespawn.Reconcile.NONE,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.UNCRAFTED, false));
		assertEquals(DragonMaceRespawn.Reconcile.NONE,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.MISSING, false));
		assertEquals(DragonMaceRespawn.Reconcile.NONE,
				DragonMaceRespawn.reconcileDecision(DragonMaceOwnership.DELETED, false));
	}

	private static DragonMaceWorldData stateOf(DragonMaceOwnership state) {
		DragonMaceWorldData data = new DragonMaceWorldData();

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

		assertEquals(state, data.getState());
		return data;
	}
}
