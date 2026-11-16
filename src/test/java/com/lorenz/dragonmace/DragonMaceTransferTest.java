package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real atomic move used by {@code /transfermace}, including its rollback.
 */
class DragonMaceTransferTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static FakeMaceInventory withMace(int size) {
		FakeMaceInventory inventory = new FakeMaceInventory(size);
		inventory.fill(0, FakeMaceInventory.mace());
		return inventory;
	}

	@Test
	void successfulTransferMovesExactlyOneStack() {
		FakeMaceInventory from = withMace(36);
		FakeMaceInventory to = new FakeMaceInventory(36);

		assertEquals(DragonMaceTransfer.Result.SUCCESS, DragonMaceTransfer.moveMace(from, to));

		assertEquals(0, from.countMarked(), "sender must no longer hold the mace");
		assertEquals(1, to.countMarked(), "target must hold exactly one mace");
		assertTrue(DragonMaceIdentity.isDragonMace(to.get(0)));
	}

	@Test
	void transferPreservesTheIdentityMarker() {
		FakeMaceInventory from = withMace(36);
		FakeMaceInventory to = new FakeMaceInventory(36);

		DragonMaceTransfer.moveMace(from, to);

		ItemStack moved = to.get(0);
		assertTrue(DragonMaceIdentity.isDragonMace(moved));
		assertEquals(DragonMaceRecipes.DRAGON_MACE_NAME, moved.getHoverName().getString());
	}

	@Test
	void fullTargetLeavesSenderUnchanged() {
		FakeMaceInventory from = withMace(36);
		FakeMaceInventory to = new FakeMaceInventory(2);
		to.fillAll(new ItemStack(Items.DIRT));

		assertEquals(DragonMaceTransfer.Result.TARGET_FULL, DragonMaceTransfer.moveMace(from, to));

		assertEquals(1, from.countMarked(), "sender must keep the mace");
		assertTrue(DragonMaceIdentity.isDragonMace(from.get(0)));
		assertEquals(0, to.countMarked());
	}

	@Test
	void targetAlreadyHoldingTheMaceIsRejected() {
		FakeMaceInventory from = withMace(36);
		FakeMaceInventory to = withMace(36);

		assertEquals(DragonMaceTransfer.Result.TARGET_HAS_MACE, DragonMaceTransfer.moveMace(from, to));

		assertEquals(1, from.countMarked());
		assertEquals(1, to.countMarked());
	}

	@Test
	void senderWithoutTheMaceIsRejected() {
		FakeMaceInventory from = new FakeMaceInventory(36);
		from.fill(0, new ItemStack(Items.MACE));
		FakeMaceInventory to = new FakeMaceInventory(36);

		assertEquals(DragonMaceTransfer.Result.NO_MACE, DragonMaceTransfer.moveMace(from, to));

		assertEquals(0, to.countMarked());
		assertFalse(DragonMaceIdentity.isDragonMace(from.get(0)), "ordinary maces are never transferred");
	}

	@Test
	void partialInsertRollsBackWithoutDuplicating() {
		FakeMaceInventory from = withMace(36);
		// Accepts the item into a slot but reports failure, the worst case for rollback.
		MaceInventory to = new MaceInventory() {
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
				this.slot = stack.copy();
				stack.setCount(0);
				return false;
			}
		};

		assertEquals(DragonMaceTransfer.Result.TARGET_FULL, DragonMaceTransfer.moveMace(from, to));

		assertEquals(1, from.countMarked(), "sender must be restored");
		assertEquals(0, DragonMaceIdentity.isDragonMace(to.get(0)) ? 1 : 0, "target must be emptied again");
	}

	@Test
	void rollbackRemovesOnlyWhatWasInserted() {
		FakeMaceInventory from = withMace(36);
		FakeMaceInventory to = new FakeMaceInventory(2, 1, false);

		DragonMaceTransfer.moveMace(from, to);

		assertEquals(1, from.countMarked() + to.countMarked(), "total mace count must stay at one");
	}

	@Test
	void ordinaryItemsAreNotMoved() {
		FakeMaceInventory from = new FakeMaceInventory(36);
		from.fill(0, new ItemStack(Items.DIAMOND, 5));
		FakeMaceInventory to = new FakeMaceInventory(36);

		assertEquals(DragonMaceTransfer.Result.NO_MACE, DragonMaceTransfer.moveMace(from, to));

		assertEquals(5, from.get(0).getCount());
		assertTrue(to.isEmpty());
	}

	@Test
	void findDragonMaceSlotIgnoresOrdinaryMaces() {
		FakeMaceInventory inventory = new FakeMaceInventory(4);
		inventory.fill(0, new ItemStack(Items.MACE));
		inventory.fill(2, FakeMaceInventory.mace());

		assertEquals(2, DragonMaceTransfer.findDragonMaceSlot(inventory));
	}

	@Test
	void onlyOneMarkedStackIsMoved() {
		FakeMaceInventory from = withMace(36);
		from.fill(5, FakeMaceInventory.mace());
		FakeMaceInventory to = new FakeMaceInventory(36);

		assertEquals(DragonMaceTransfer.Result.SUCCESS, DragonMaceTransfer.moveMace(from, to));

		assertEquals(1, from.countMarked(), "the second marked stack must stay with the sender");
		assertEquals(1, to.countMarked(), "the target must receive exactly one stack");
	}

	@Test
	void validateAcceptsThePersistentOwner() {
		UUID owner = UUID.randomUUID();
		UUID target = UUID.randomUUID();

		assertEquals(DragonMaceTransfer.Result.SUCCESS,
				DragonMaceTransfer.validate(owner, target, owner, false));
	}

	@Test
	void validateRejectsSelfTransfer() {
		UUID owner = UUID.randomUUID();

		assertEquals(DragonMaceTransfer.Result.SELF_TRANSFER,
				DragonMaceTransfer.validate(owner, owner, owner, false));
	}

	@Test
	void validateRejectsOfflineTarget() {
		UUID owner = UUID.randomUUID();

		assertEquals(DragonMaceTransfer.Result.TARGET_OFFLINE,
				DragonMaceTransfer.validate(owner, null, owner, false));
	}

	@Test
	void validateRejectsANonOwner() {
		UUID owner = UUID.randomUUID();
		UUID sender = UUID.randomUUID();

		assertEquals(DragonMaceTransfer.Result.NOT_OWNER,
				DragonMaceTransfer.validate(sender, UUID.randomUUID(), owner, false));
	}

	@Test
	void validateRejectsWhenNoOwnerIsPersisted() {
		assertEquals(DragonMaceTransfer.Result.NOT_OWNER,
				DragonMaceTransfer.validate(UUID.randomUUID(), UUID.randomUUID(), null, false));
	}

	@Test
	void validateRejectsATargetThatAlreadyHoldsTheMace() {
		UUID owner = UUID.randomUUID();

		assertEquals(DragonMaceTransfer.Result.TARGET_HAS_MACE,
				DragonMaceTransfer.validate(owner, UUID.randomUUID(), owner, true));
	}
}
