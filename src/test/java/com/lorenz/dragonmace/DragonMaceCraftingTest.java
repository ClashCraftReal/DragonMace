package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the single crafting gate that every creation path shares.
 *
 * <p>The gate is pure state logic, so these tests cover the decision rather than the mixin wiring:
 * the mixins only forward the live server and player into {@link DragonMaceCrafting}.
 */
class DragonMaceCraftingTest {
	private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;

	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static ItemStack dragonMace() {
		return DragonMaceRecipes.createDragonMaceStack();
	}

	private static DragonMaceWorldData inState(DragonMaceOwnership state) {
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

	@Test
	void freshWorldAllowsCrafting() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.UNCRAFTED);

		assertTrue(DragonMaceCrafting.canCraft(data.getState()));
		assertFalse(DragonMaceCrafting.suppressesResult(data.getState(), dragonMace()));
	}

	@Test
	void everyNonFreshStateRejectsCrafting() {
		for (DragonMaceOwnership state : new DragonMaceOwnership[] {
				DragonMaceOwnership.PRESENT, DragonMaceOwnership.MISSING, DragonMaceOwnership.DELETED }) {
			DragonMaceWorldData data = inState(state);

			assertFalse(DragonMaceCrafting.canCraft(data.getState()), state + " must reject crafting");
			assertTrue(DragonMaceCrafting.suppressesResult(data.getState(), dragonMace()),
					state + " must hide the crafting result");
		}
	}

	@Test
	void suppressionOnlyAppliesToTheMarkedMace() {
		assertFalse(DragonMaceCrafting.suppressesResult(DragonMaceOwnership.PRESENT, new ItemStack(Items.MACE)),
				"an ordinary mace must never be suppressed");
		assertFalse(DragonMaceCrafting.suppressesResult(DragonMaceOwnership.PRESENT, ItemStack.EMPTY));
		assertFalse(DragonMaceCrafting.suppressesResult(DragonMaceOwnership.PRESENT, new ItemStack(Items.DIAMOND)));
	}

	@Test
	void craftAfterSpawnIsRejected() {
		DragonMaceWorldData data = new DragonMaceWorldData();

		// /spawndragonmace moves UNCRAFTED straight to PRESENT.
		assertTrue(data.markPresent());

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void craftAfterFirstCraftIsRejected() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.PRESENT);

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
		assertFalse(data.markCrafted(), "a second craft must not move the state again");
	}

	@Test
	void craftAfterTransferIsRejected() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.PRESENT);
		data.setOwner(UUID.randomUUID());

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void craftAfterRecoveryIsRejected() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.MISSING);

		assertTrue(data.markPresent(), "recovery must restore PRESENT");

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void craftAfterRespawnIsRejected() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.MISSING);

		assertTrue(DragonMaceRespawn.commitSpawn(data, true));
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void craftAfterDeletionIsRejected() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.DELETED);

		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
		assertFalse(data.markCrafted(), "a deleted mace must not be recreated by crafting");
	}

	@Test
	void craftingIsAllowedAgainAfterDeletionReset() {
		UUID previousOwner = UUID.randomUUID();
		UUID newOwner = UUID.randomUUID();
		DragonMaceWorldData data = inState(DragonMaceOwnership.DELETED);
		data.setOwner(previousOwner);

		assertTrue(data.resetToUncrafted(), "a deleted world may be reset by an operator");
		assertEquals(DragonMaceOwnership.UNCRAFTED, data.getState());
		assertNull(data.getOwner(), "the previous owner must be cleared");

		assertTrue(DragonMaceCrafting.canCraft(data.getState()));
		assertFalse(DragonMaceCrafting.suppressesResult(data.getState(), dragonMace()));

		// The next successful craft assigns the new owner normally.
		assertTrue(data.markCrafted());
		data.setOwner(newOwner);

		assertEquals(DragonMaceOwnership.PRESENT, data.getState());
		assertTrue(data.isOwner(newOwner));
		assertFalse(data.isOwner(previousOwner));
		assertFalse(DragonMaceCrafting.canCraft(data.getState()));
	}

	@Test
	void rejectionSurvivesARestart() {
		DragonMaceWorldData data = inState(DragonMaceOwnership.PRESENT);
		data.setOwner(UUID.randomUUID());

		Tag encoded = DragonMaceWorldData.CODEC.encodeStart(OPS, data).getOrThrow();
		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(OPS, encoded).getOrThrow();

		assertEquals(DragonMaceOwnership.PRESENT, decoded.getState());
		assertFalse(DragonMaceCrafting.canCraft(decoded.getState()));
		assertEquals(data.getOwner(), decoded.getOwner());
	}
}
