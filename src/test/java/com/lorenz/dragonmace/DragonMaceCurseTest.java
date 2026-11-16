package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceCurseTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	@Test
	void firstActivationSucceeds() {
		assertEquals(DragonMaceCurse.ActivateResult.ACTIVATED,
				DragonMaceCurse.decideActivation(null, UUID.randomUUID()));
	}

	@Test
	void samePlayerIsAlreadyActive() {
		UUID player = UUID.randomUUID();

		assertEquals(DragonMaceCurse.ActivateResult.ALREADY_ACTIVE,
				DragonMaceCurse.decideActivation(player, player));
	}

	@Test
	void secondPlayerIsRefusedWhileTheCurseIsHeld() {
		assertEquals(DragonMaceCurse.ActivateResult.TAKEN,
				DragonMaceCurse.decideActivation(UUID.randomUUID(), UUID.randomUUID()));
	}

	@Test
	void missingPlayerIsRejected() {
		assertEquals(DragonMaceCurse.ActivateResult.NO_PLAYER,
				DragonMaceCurse.decideActivation(null, null));
	}

	@Test
	void curseOwnerRoundTripsThroughTheSavedData() {
		UUID owner = UUID.randomUUID();
		DragonMaceWorldData data = new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(),
				Optional.empty(), Optional.of(owner.toString()));

		assertEquals(owner, data.getCurseOwner());
		assertTrue(data.isCurseOwner(owner));
		assertFalse(data.isCurseOwner(UUID.randomUUID()));
	}

	@Test
	void absentCurseOwnerIsNull() {
		DragonMaceWorldData data = new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(),
				Optional.empty(), Optional.empty());

		assertNull(data.getCurseOwner());
	}

	@Test
	void malformedCurseOwnerIsTreatedAsInactive() {
		DragonMaceWorldData data = new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(),
				Optional.empty(), Optional.of("not-a-uuid"));

		assertNull(data.getCurseOwner());
	}

	@Test
	void settingTheCurseOwnerPersistsTheUuid() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		UUID owner = UUID.randomUUID();

		data.setCurseOwner(owner);

		assertEquals(owner, data.getCurseOwner());
	}

	@Test
	void clearingTheCurseOwnerDeactivatesTheCurse() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.setCurseOwner(UUID.randomUUID());

		data.setCurseOwner(null);

		assertNull(data.getCurseOwner());
	}
}
