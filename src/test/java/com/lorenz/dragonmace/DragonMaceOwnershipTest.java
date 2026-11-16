package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class DragonMaceOwnershipTest {
	@Test
	void craftingMovesUncraftedToPresent() {
		assertTrue(DragonMaceOwnership.UNCRAFTED.canTransitionTo(DragonMaceOwnership.PRESENT));
	}

	@Test
	void destructionMovesPresentToMissing() {
		assertTrue(DragonMaceOwnership.PRESENT.canTransitionTo(DragonMaceOwnership.MISSING));
	}

	@Test
	void recoveryMovesMissingToPresent() {
		assertTrue(DragonMaceOwnership.MISSING.canTransitionTo(DragonMaceOwnership.PRESENT));
	}

	@Test
	void administrativeDeletionIsAllowedFromPresentAndMissing() {
		assertTrue(DragonMaceOwnership.PRESENT.canTransitionTo(DragonMaceOwnership.DELETED));
		assertTrue(DragonMaceOwnership.MISSING.canTransitionTo(DragonMaceOwnership.DELETED));
	}

	@Test
	void administrativeSpawnRecoversDeleted() {
		assertTrue(DragonMaceOwnership.DELETED.canTransitionTo(DragonMaceOwnership.PRESENT));
	}

	@Test
	void administrativeResetReturnsDeletedToUncrafted() {
		assertTrue(DragonMaceOwnership.DELETED.canTransitionTo(DragonMaceOwnership.UNCRAFTED));
	}

	@Test
	void selfTransitionsAreRejected() {
		for (DragonMaceOwnership state : DragonMaceOwnership.values()) {
			assertFalse(state.canTransitionTo(state), state + " must not transition to itself");
		}
	}

	@Test
	void invalidTransitionsAreRejected() {
		assertFalse(DragonMaceOwnership.UNCRAFTED.canTransitionTo(DragonMaceOwnership.MISSING));
		assertFalse(DragonMaceOwnership.UNCRAFTED.canTransitionTo(DragonMaceOwnership.DELETED));
		assertFalse(DragonMaceOwnership.PRESENT.canTransitionTo(DragonMaceOwnership.UNCRAFTED));
		assertFalse(DragonMaceOwnership.MISSING.canTransitionTo(DragonMaceOwnership.UNCRAFTED));
		assertFalse(DragonMaceOwnership.DELETED.canTransitionTo(DragonMaceOwnership.MISSING));
	}

	@Test
	void identifiersRoundTrip() {
		for (DragonMaceOwnership state : DragonMaceOwnership.values()) {
			assertEquals(Optional.of(state), DragonMaceOwnership.byId(state.id()));
		}

		assertEquals(Optional.empty(), DragonMaceOwnership.byId("nonsense"));
	}
}
