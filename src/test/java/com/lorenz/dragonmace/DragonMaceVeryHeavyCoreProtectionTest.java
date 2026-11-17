package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Storage protection for the Very Heavy Core.
 *
 * <p>The core reuses the Dragon Egg protection infrastructure rather than a parallel system, so these
 * assert that every existing guard now also covers it while ordinary paper stays unaffected.
 */
class DragonMaceVeryHeavyCoreProtectionTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack core() {
		return DragonMaceItems.createVeryHeavyCoreStack();
	}

	@Test
	void theCoreIsAProtectedStack() {
		assertTrue(DragonMaceProtection.isProtectedStack(core()));
	}

	@Test
	void ordinaryPaperIsNotProtected() {
		assertFalse(DragonMaceProtection.isProtectedStack(new ItemStack(Items.PAPER)));
	}

	@Test
	void aChestRefusesTheCore() {
		assertTrue(DragonMaceProtection.blocksStorage(new SimpleContainer(27), core()));
	}

	@Test
	void aShelfRefusesTheCore() {
		assertTrue(DragonMaceProtection.blocksShelfInsert(core()));
	}

	@Test
	void aBundleRefusesTheCore() {
		assertTrue(DragonMaceProtection.blocksBundleInsert(core()));
	}

	@Test
	void aQuickMoveIntoStorageIsRefused() {
		SimpleContainer chest = new SimpleContainer(27);
		List<Slot> slots = List.of(new Slot(chest, 0, 0, 0), new Slot(chest, 1, 0, 0));

		assertTrue(DragonMaceProtection.blocksQuickMove(slots, 0, 2, core()));
	}

	@Test
	void aNumberKeySwapIntoStorageIsRefused() {
		SimpleContainer chest = new SimpleContainer(27);
		Slot slot = new Slot(chest, 0, 0, 0);

		assertTrue(DragonMaceProtection.blocksSwapInto(slot, 0, core()));
	}

	@Test
	void thePlayerInventoryStillAcceptsTheCore() {
		Slot slot = new Slot(new Inventory(null, null), 0, 0, 0);

		assertFalse(DragonMaceProtection.blocksSlotStorage(slot, core()),
				"normal player inventory movement must keep working");
	}

	@Test
	void theCoreGainsPermanentGroundProtection() {
		DragonMaceProtection.GroundProtection protection = DragonMaceProtection.groundProtection(core());

		assertTrue(protection.unlimitedLifetime(),
				"a dropped Very Heavy Core must never despawn");
		assertTrue(protection.invulnerable(),
				"a dropped Very Heavy Core must be immune to fire and lava");
	}

	@Test
	void theDragonEggKeepsItsGroundProtection() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(new ItemStack(Items.DRAGON_EGG));

		assertTrue(protection.unlimitedLifetime());
		assertTrue(protection.invulnerable());
	}
}
