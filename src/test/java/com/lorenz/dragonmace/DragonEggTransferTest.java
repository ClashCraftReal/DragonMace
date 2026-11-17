package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for every Dragon Egg storage bypass.
 *
 * <p>Each test drives the same pure decision the mixins call, with real {@link Slot} objects over a
 * real chest container, a real crafting container and a player-inventory container. The decisions are
 * made before any transfer runs, so a {@code true} result means the source stack is never touched.
 */
class DragonEggTransferTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack egg() {
		return new ItemStack(Items.DRAGON_EGG);
	}

	private static Slot chestSlot() {
		return new Slot(new SimpleContainer(27), 0, 0, 0);
	}

	private static Slot craftingSlot() {
		return new Slot(new TestCraftingContainer(), 0, 0, 0);
	}

	/** A slot backed by a real player inventory; the inventory only needs to exist, not a player. */
	private static Slot playerSlot() {
		return new Slot(new Inventory(null, null), 0, 0, 0);
	}

	// ------------------------------------------------------- offhand and hotbar

	@Test
	void offhandSwapIntoAChestIsRejected() {
		assertTrue(DragonMaceProtection.blocksSwapInto(chestSlot(), Inventory.SLOT_OFFHAND, egg()));
	}

	@Test
	void hotbarSwapIntoAChestIsRejected() {
		for (int button = 0; button < Inventory.SELECTION_SIZE; button++) {
			assertTrue(DragonMaceProtection.blocksSwapInto(chestSlot(), button, egg()),
					"hotbar button " + button + " must be rejected");
		}
	}

	@Test
	void offhandSwapIntoThePlayerInventoryIsAllowed() {
		assertFalse(DragonMaceProtection.blocksSwapInto(playerSlot(), Inventory.SLOT_OFFHAND, egg()));
	}

	@Test
	void anOrdinaryItemCanStillBeSwappedIntoAChest() {
		assertFalse(DragonMaceProtection.blocksSwapInto(chestSlot(), Inventory.SLOT_OFFHAND,
				new ItemStack(Items.STICK)));
	}

	@Test
	void anInvalidSwapButtonIsIgnored() {
		assertFalse(DragonMaceProtection.blocksSwapInto(chestSlot(), -1, egg()));
		assertFalse(DragonMaceProtection.blocksSwapInto(chestSlot(), 12, egg()));
	}

	// ------------------------------------------------------------- click paths

	@Test
	void aCarriedEggCannotBePlacedInAChestSlot() {
		assertTrue(DragonMaceProtection.blocksSlotStorage(chestSlot(), egg()));
	}

	@Test
	void aCarriedEggCanBePlacedInThePlayerInventory() {
		assertFalse(DragonMaceProtection.blocksSlotStorage(playerSlot(), egg()));
	}

	@Test
	void shiftClickIntoStorageIsRejected() {
		List<Slot> slots = List.of(chestSlot(), chestSlot(), playerSlot());

		assertTrue(DragonMaceProtection.blocksQuickMove(slots, 0, 2, egg()));
		assertFalse(DragonMaceProtection.blocksQuickMove(slots, 2, 3, egg()));
	}

	@Test
	void dragIntoStorageIsRejected() {
		assertTrue(DragonMaceProtection.blocksSlotStorage(chestSlot(), egg()));
	}

	// ------------------------------------------------------------------ shelves

	@Test
	void aShelfRefusesTheEgg() {
		assertTrue(DragonMaceProtection.blocksShelfInsert(egg()));
	}

	@Test
	void aShelfStillAcceptsOrdinaryItems() {
		assertFalse(DragonMaceProtection.blocksShelfInsert(new ItemStack(Items.STICK)));
		assertFalse(DragonMaceProtection.blocksShelfInsert(ItemStack.EMPTY));
	}

	// ------------------------------------------------------------------ bundles

	@Test
	void aBundleRefusesTheEgg() {
		assertTrue(DragonMaceProtection.blocksBundleInsert(egg()));
	}

	@Test
	void aBundleStillAcceptsOrdinaryItems() {
		assertFalse(DragonMaceProtection.blocksBundleInsert(new ItemStack(Items.STICK)));
	}

	// ------------------------------------------------------------ crafting grid

	@Test
	void theCraftingGridStillAcceptsTheEgg() {
		assertFalse(DragonMaceProtection.blocksSlotStorage(craftingSlot(), egg()));
		assertTrue(DragonMaceProtection.isAllowedContainer(new TestCraftingContainer()));
	}

	// ------------------------------------------------- no loss on a rejected move

	@Test
	void aRejectedSwapLeavesTheSourceStackIntact() {
		ItemStack offhand = egg();
		Slot chest = chestSlot();
		chest.set(egg());

		assertTrue(DragonMaceProtection.blocksSwapInto(chest, Inventory.SLOT_OFFHAND, offhand));

		assertEquals(1, offhand.getCount(), "the source egg must not be consumed");
		assertEquals(1, chest.getItem().getCount(), "the destination must not be overwritten");
	}

	@Test
	void aRejectedQuickMoveLeavesTheSourceStackIntact() {
		ItemStack source = egg();
		List<Slot> slots = List.of(chestSlot());

		assertTrue(DragonMaceProtection.blocksQuickMove(slots, 0, 1, source));
		assertEquals(1, source.getCount());
	}

	// ------------------------------------------------------------- utilities

	private static final class TestCraftingContainer implements CraftingContainer {
		private final SimpleContainer delegate = new SimpleContainer(9);

		@Override
		public int getWidth() {
			return 3;
		}

		@Override
		public int getHeight() {
			return 3;
		}

		@Override
		public List<ItemStack> getItems() {
			return List.of();
		}

		@Override
		public int getContainerSize() {
			return this.delegate.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return this.delegate.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.delegate.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return this.delegate.removeItem(slot, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return this.delegate.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.delegate.setItem(slot, stack);
		}

		@Override
		public void setChanged() {
			this.delegate.setChanged();
		}

		@Override
		public boolean stillValid(net.minecraft.world.entity.player.Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			this.delegate.clearContent();
		}

		@Override
		public void fillStackedContents(net.minecraft.world.entity.player.StackedItemContents contents) {
		}
	}
}
