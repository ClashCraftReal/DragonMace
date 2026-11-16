package com.lorenz.dragonmace;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal inventory surface used by the transfer algorithm.
 *
 * <p>Extracted so the atomic move and its rollback can be exercised directly in tests instead of
 * only through a live {@code ServerPlayer}.
 */
public interface MaceInventory {
	int size();

	ItemStack get(int slot);

	void set(int slot, ItemStack stack);

	/** First empty slot, or {@code -1} when the inventory is full. */
	int freeSlot();

	/**
	 * Adds the stack, mutating it to whatever could not be inserted.
	 *
	 * @return {@code true} when the whole stack was inserted
	 */
	boolean add(ItemStack stack);

	static MaceInventory of(Inventory inventory) {
		return new MaceInventory() {
			@Override
			public int size() {
				return inventory.getContainerSize();
			}

			@Override
			public ItemStack get(int slot) {
				return inventory.getItem(slot);
			}

			@Override
			public void set(int slot, ItemStack stack) {
				inventory.setItem(slot, stack);
			}

			@Override
			public int freeSlot() {
				return inventory.getFreeSlot();
			}

			@Override
			public boolean add(ItemStack stack) {
				return inventory.add(stack);
			}
		};
	}
}
