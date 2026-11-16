package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DragonMaceConfigTest {
	@Test
	void defaultsToEndFountain() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN, DragonMaceConfig.defaults().respawnLocation());
	}

	@Test
	void parsesEndFountain() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("{\"respawnLocation\": \"end_fountain\"}").respawnLocation());
	}

	@Test
	void parsesWorldSpawn() {
		assertEquals(DragonMaceConfig.RespawnLocation.WORLD_SPAWN,
				DragonMaceConfig.parse("{\"respawnLocation\": \"world_spawn\"}").respawnLocation());
	}

	@Test
	void invalidValueFallsBackToDefault() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("{\"respawnLocation\": \"the_moon\"}").respawnLocation());
	}

	@Test
	void missingKeyFallsBackToDefault() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("{\"unrelated\": true}").respawnLocation());
	}

	@Test
	void malformedJsonFallsBackToDefault() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("{not json").respawnLocation());
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("[1, 2, 3]").respawnLocation());
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse("{\"respawnLocation\": 7}").respawnLocation());
	}

	@Test
	void blankInputFallsBackToDefault() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN, DragonMaceConfig.parse("").respawnLocation());
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN, DragonMaceConfig.parse(null).respawnLocation());
	}

	@Test
	void defaultJsonRoundTrips() {
		assertEquals(DragonMaceConfig.RespawnLocation.END_FOUNTAIN,
				DragonMaceConfig.parse(DragonMaceConfig.defaultJson()).respawnLocation());
	}
}
