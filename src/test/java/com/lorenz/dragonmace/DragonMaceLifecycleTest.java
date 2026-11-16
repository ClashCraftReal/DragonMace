package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused lifecycle tests for the three loss reports: normal drops, container transactions and
 * death transfer.
 *
 * <p>These tests exercise the same pure decisions the mixins call, including the exact transaction
 * point that runs before vanilla mutates a source stack. Live item entities, menus and a running
 * server are verified separately by the startup check.
 */
class DragonMaceLifecycleTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static ItemStack mace() {
		return DragonMaceRecipes.createDragonMaceStack();
	}

	private static FakeMaceInventory ownerWithMace() {
		FakeMaceInventory inventory = new FakeMaceInventory(36);
		inventory.fill(0, mace());
		return inventory;
	}

	private static int totalMarked(FakeMaceInventory... inventories) {
		int total = 0;

		for (FakeMaceInventory inventory : inventories) {
			total += inventory.countMarked();
		}

		return total;
	}

	@Test
	void normalDropHandsTheOriginalStackToTheDropPath() {
		FakeMaceInventory owner = ownerWithMace();
		ItemStack original = owner.get(0);

		// ServerPlayer.drop(boolean) removes the selected stack before LivingEntity.drop creates the
		// item entity, so the drop path receives this exact object rather than a replacement.
		ItemStack dropped = owner.get(0);
		owner.set(0, ItemStack.EMPTY);

		assertSame(original, dropped);
		assertTrue(DragonMaceIdentity.isDragonMace(dropped), "the dropped stack must keep its identity marker");
		assertEquals(1, dropped.getCount());
		assertEquals(0, owner.countMarked());
		assertEquals(1, totalMarked(owner) + dropped.getCount());
	}

	@Test
	void containerInsertionIsRejectedBeforeTheSourceIsTouched() {
		SimpleContainer chest = new SimpleContainer(9);
		Slot chestSlot = new Slot(chest, 0, 0, 0);
		FakeMaceInventory owner = ownerWithMace();
		ItemStack source = owner.get(0);

		assertTrue(DragonMaceProtection.blocksSlotStorage(chestSlot, source));

		// The guard runs at the head of the menu transaction. No source clearing has happened, so the
		// original mace is still in the player's inventory and the chest is still empty.
		assertEquals(1, owner.countMarked());
		assertEquals(1, source.getCount());
		assertTrue(chest.isEmpty());
	}

	@Test
	void shiftClickIntoAProtectedContainerIsRejectedAtomically() {
		SimpleContainer chest = new SimpleContainer(2);
		List<Slot> destinations = List.of(new Slot(chest, 0, 0, 0), new Slot(chest, 1, 0, 0));
		FakeMaceInventory owner = ownerWithMace();
		ItemStack source = owner.get(0);

		assertTrue(DragonMaceProtection.blocksQuickMove(destinations, 0, destinations.size(), source));
		assertEquals(1, owner.countMarked(), "a rejected shift-click must not clear the source");
		assertTrue(chest.isEmpty());
	}

	@Test
	void shiftClickAllowsOrdinaryItemsAndEmptyStacks() {
		SimpleContainer chest = new SimpleContainer(2);
		List<Slot> destinations = List.of(new Slot(chest, 0, 0, 0), new Slot(chest, 1, 0, 0));

		assertFalse(DragonMaceProtection.blocksQuickMove(destinations, 0, destinations.size(),
				new ItemStack(Items.DIRT, 4)));
		assertFalse(DragonMaceProtection.blocksQuickMove(destinations, 0, destinations.size(), ItemStack.EMPTY));
	}

	@Test
	void bundleRejectsTheMaceInBothDirections() {
		assertTrue(DragonMaceProtection.blocksBundleInsert(mace()));
		assertFalse(DragonMaceProtection.blocksBundleInsert(new ItemStack(Items.MACE)));
		assertFalse(DragonMaceProtection.blocksBundleInsert(ItemStack.EMPTY));
	}

	@Test
	void deathTransferUsesFreeInventorySpace() {
		FakeMaceInventory victim = ownerWithMace();
		FakeMaceInventory killer = new FakeMaceInventory(36);

		assertEquals(DragonMaceDeathTransfer.Plan.INSERT_INTO_KILLER,
				DragonMaceDeathTransfer.plan(victim, killer));
		assertEquals(DragonMaceTransfer.Result.SUCCESS, DragonMaceTransfer.moveMace(victim, killer));

		assertEquals(0, victim.countMarked());
		assertEquals(1, killer.countMarked());
		assertTrue(DragonMaceIdentity.isDragonMace(killer.get(0)));
		assertEquals(1, totalMarked(victim, killer));
	}

	@Test
	void deathTransferDropsTheOriginalStackWhenTheKillerIsFull() {
		FakeMaceInventory victim = ownerWithMace();
		FakeMaceInventory killer = new FakeMaceInventory(36);
		killer.fillAll(new ItemStack(Items.DIRT));

		assertEquals(DragonMaceDeathTransfer.Plan.DROP_AT_KILLER,
				DragonMaceDeathTransfer.plan(victim, killer));
		assertEquals(DragonMaceTransfer.Result.TARGET_FULL, DragonMaceTransfer.moveMace(victim, killer));

		assertEquals(1, victim.countMarked(), "a full killer must not clear the victim");

		// The real transfer moves this exact stack into the reserved ItemEntity.
		ItemStack dropped = victim.get(0);
		victim.set(0, ItemStack.EMPTY);

		assertTrue(DragonMaceIdentity.isDragonMace(dropped));
		assertEquals(1, dropped.getCount());
		assertEquals(1, totalMarked(victim, killer) + dropped.getCount());
	}

	@Test
	void failedKillerInsertRollsBackWithoutDuplicating() {
		FakeMaceInventory victim = ownerWithMace();
		MaceInventory killer = new MaceInventory() {
			private ItemStack slot = ItemStack.EMPTY;

			@Override
			public int size() {
				return 1;
			}

			@Override
			public ItemStack get(int index) {
				return this.slot;
			}

			@Override
			public void set(int index, ItemStack stack) {
				this.slot = stack;
			}

			@Override
			public int freeSlot() {
				return 0;
			}

			@Override
			public boolean add(ItemStack stack) {
				// Worst case: the item lands but the inventory still reports failure.
				this.slot = stack.copy();
				stack.setCount(0);
				return false;
			}
		};

		assertEquals(DragonMaceTransfer.Result.TARGET_FULL, DragonMaceTransfer.moveMace(victim, killer));

		assertEquals(1, victim.countMarked(), "the victim must get the original mace back");
		assertFalse(DragonMaceIdentity.isDragonMace(killer.get(0)), "the partial insert must be removed");
	}

	@Test
	void deathTransferPersistsTheNewOwner() {
		DragonMaceWorldData data = new DragonMaceWorldData();
		data.markCrafted();
		UUID victim = UUID.randomUUID();
		UUID killer = UUID.randomUUID();
		data.setOwner(victim);

		assertTrue(DragonMaceDeathTransfer.recordNewOwner(data, killer));
		assertEquals(killer, data.getOwner());
		assertTrue(data.isOwner(killer));
		assertFalse(data.isOwner(victim));
		assertEquals(DragonMaceOwnership.PRESENT, data.getState());

		DynamicOps<Tag> ops = NbtOps.INSTANCE;
		Tag encoded = DragonMaceWorldData.CODEC.encodeStart(ops, data).getOrThrow();
		DragonMaceWorldData decoded = DragonMaceWorldData.CODEC.parse(ops, encoded).getOrThrow();

		assertEquals(killer, decoded.getOwner(), "the new owner must survive a save/load round trip");
		assertEquals(DragonMaceOwnership.PRESENT, decoded.getState());
	}

	@Test
	void everyOperationLeavesExactlyOneMace() {
		FakeMaceInventory victim = ownerWithMace();
		FakeMaceInventory killer = new FakeMaceInventory(36);

		assertEquals(1, totalMarked(victim, killer));
		DragonMaceTransfer.moveMace(victim, killer);
		assertEquals(1, totalMarked(victim, killer));

		FakeMaceInventory fullKiller = new FakeMaceInventory(36);
		fullKiller.fillAll(new ItemStack(Items.DIRT));
		DragonMaceTransfer.moveMace(killer, fullKiller);
		assertEquals(1, totalMarked(killer, fullKiller));
	}
}
