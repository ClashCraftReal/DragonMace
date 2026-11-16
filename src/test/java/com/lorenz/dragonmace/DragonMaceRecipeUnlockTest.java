package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.Recipe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceRecipeUnlockTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	@Test
	void unlockKeyPointsAtTheRegisteredRecipe() {
		assertEquals(ResourceKey.create(Registries.RECIPE, DragonMaceRecipes.VERY_HEAVY_CORE_RECIPE),
				DragonMaceRecipes.VERY_HEAVY_CORE_KEY);
		assertEquals("dragon-mace:very_heavy_core",
				DragonMaceRecipes.VERY_HEAVY_CORE_KEY.identifier().toString());
	}

	@Test
	void unlockKeyIsARegistryRecipeKey() {
		ResourceKey<Recipe<?>> key = DragonMaceRecipes.VERY_HEAVY_CORE_KEY;

		assertEquals(Registries.RECIPE, key.registryKey());
	}

	@Test
	void unlockingWithoutAPlayerIsRefused() {
		assertFalse(DragonMaceRecipeUnlock.unlockVeryHeavyCore(null));
		assertFalse(DragonMaceRecipeUnlock.unlockIfHoldingEgg(null));
		assertFalse(DragonMaceRecipeUnlock.hasDragonEgg(null));
	}
}
