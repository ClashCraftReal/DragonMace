package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceProtectionTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	@Test
	void dragonMaceIsProtected() {
		assertTrue(DragonMaceProtection.isProtectedStack(DragonMaceRecipes.createDragonMaceStack()));
	}

	@Test
	void ordinaryMaceIsNotProtected() {
		assertFalse(DragonMaceProtection.isProtectedStack(new ItemStack(Items.MACE)));
		assertFalse(DragonMaceProtection.isProtectedStack(ItemStack.EMPTY));
	}

	@Test
	void genericContainersAreRefused() {
		SimpleContainer chest = new SimpleContainer(27);

		assertFalse(DragonMaceProtection.isAllowedContainer(chest));
		assertTrue(DragonMaceProtection.blocksStorage(chest, DragonMaceRecipes.createDragonMaceStack()));
	}

	@Test
	void ordinaryItemsMayStillEnterContainers() {
		SimpleContainer chest = new SimpleContainer(27);

		assertFalse(DragonMaceProtection.blocksStorage(chest, new ItemStack(Items.MACE)));
		assertFalse(DragonMaceProtection.blocksStorage(chest, new ItemStack(Items.DIAMOND)));
	}

	@Test
	void emptyStackIsNeverBlocked() {
		SimpleContainer chest = new SimpleContainer(27);

		assertFalse(DragonMaceProtection.blocksStorage(chest, ItemStack.EMPTY));
	}

	@Test
	void droppedDragonMaceGetsUnlimitedLifetimeAndInvulnerability() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(DragonMaceRecipes.createDragonMaceStack());

		assertTrue(protection.unlimitedLifetime(), "the mace must not despawn naturally");
		assertTrue(protection.invulnerable(), "fire, lava and explosion damage must be ignored");
	}

	@Test
	void ordinaryItemDropsKeepNormalLifetimeAndVulnerability() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(new ItemStack(Items.MACE));

		assertFalse(protection.unlimitedLifetime());
		assertFalse(protection.invulnerable());
	}

	@Test
	void emptyStacksGetNoGroundProtection() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(ItemStack.EMPTY);

		assertFalse(protection.unlimitedLifetime());
		assertFalse(protection.invulnerable());
	}
}
