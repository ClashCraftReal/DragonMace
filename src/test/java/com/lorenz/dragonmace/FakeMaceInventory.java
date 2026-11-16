package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * Test double for {@link MaceInventory} with real slot semantics, including partial inserts.
 */
final class FakeMaceInventory implements MaceInventory {
	private final List<ItemStack> slots;
	private final int maxStackSize;
	private final boolean allowPartialInsert;

	FakeMaceInventory(int size) {
		this(size, 1, false);
	}

	FakeMaceInventory(int size, int maxStackSize, boolean allowPartialInsert) {
		this.slots = new ArrayList<>(size);
		this.maxStackSize = maxStackSize;
		this.allowPartialInsert = allowPartialInsert;

		for (int i = 0; i < size; i++) {
			this.slots.add(ItemStack.EMPTY);
		}
	}

	@Override
	public int size() {
		return this.slots.size();
	}

	@Override
	public ItemStack get(int slot) {
		return this.slots.get(slot);
	}

	@Override
	public void set(int slot, ItemStack stack) {
		this.slots.set(slot, stack);
	}

	@Override
	public int freeSlot() {
		for (int i = 0; i < this.slots.size(); i++) {
			if (this.slots.get(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	@Override
	public boolean add(ItemStack stack) {
		for (int i = 0; i < this.slots.size() && !stack.isEmpty(); i++) {
			ItemStack existing = this.slots.get(i);

			if (existing.isEmpty()) {
				int moved = Math.min(this.maxStackSize, stack.getCount());
				ItemStack placed = stack.copy();
				placed.setCount(moved);
				this.slots.set(i, placed);
				stack.shrink(moved);
			} else if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < this.maxStackSize) {
				int moved = Math.min(this.maxStackSize - existing.getCount(), stack.getCount());
				existing.grow(moved);
				stack.shrink(moved);
			}

			if (!this.allowPartialInsert) {
				break;
			}
		}

		return stack.isEmpty();
	}

	int countMarked() {
		int total = 0;

		for (ItemStack stack : this.slots) {
			if (DragonMaceIdentity.isDragonMace(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	boolean isEmpty() {
		return this.slots.stream().allMatch(ItemStack::isEmpty);
	}

	void fill(int slot, ItemStack stack) {
		this.slots.set(slot, stack);
	}

	void fillAll(ItemStack stack) {
		for (int i = 0; i < this.slots.size(); i++) {
			this.slots.set(i, stack.copy());
		}
	}

	static ItemStack mace() {
		return DragonMaceRecipes.createDragonMaceStack();
	}

	static void assertSingleMace(FakeMaceInventory inventory) {
		assertEquals(1, inventory.countMarked(), "exactly one marked mace must exist");
		assertTrue(inventory.countMarked() <= 1);
	}
}
