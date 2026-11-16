package com.lorenz.dragonmace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dragons Pride: the Curse of the Dragons holder may carry at most two Totems of Undying.
 *
 * <p>The limit is counted across the whole player inventory, which already covers the main inventory,
 * the hotbar and the offhand slot. The two permitted totems keep working normally because nothing
 * touches the totem's own behaviour; only the count is capped.
 *
 * <p>Enforcement has two layers: {@link #allowsAdd} refuses a pickup or an insert that would exceed
 * the limit, and {@link #enforce} is the tail net that drops any excess that still arrived through a
 * path the first layer cannot see.
 */
public final class DragonMaceTotemLimit {
	public static final int MAX_TOTEMS = 2;

	private DragonMaceTotemLimit() {
	}

	public static boolean isTotem(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING);
	}

	/** Total totems carried, across main inventory, hotbar and offhand. */
	public static int countTotems(Inventory inventory) {
		if (inventory == null) {
			return 0;
		}

		int total = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (isTotem(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** How many totems must be removed to satisfy the limit. */
	public static int excess(Inventory inventory) {
		return excess(countTotems(inventory));
	}

	/** Pure excess calculation, kept separate so the limit can be tested without an inventory. */
	public static int excess(int carried) {
		return Math.max(0, carried - MAX_TOTEMS);
	}

	/**
	 * Pure decision for a single insertion.
	 *
	 * @param carried the totems already held
	 * @param incoming the stack that wants to enter
	 * @return {@code true} when the insertion may proceed
	 */
	public static boolean allowsAdd(int carried, ItemStack incoming) {
		if (!isTotem(incoming)) {
			return true;
		}

		return carried + incoming.getCount() <= MAX_TOTEMS;
	}

	/** Live decision for a pickup or insert into {@code inventory}. */
	public static boolean allowsAdd(Inventory inventory, ItemStack incoming) {
		return allowsAdd(countTotems(inventory), incoming);
	}

	/**
	 * Drops every totem above the limit.
	 *
	 * <p>Totems are removed from the end of the inventory first, so the hotbar and the offhand keep
	 * their totems and the player loses the least convenient ones.
	 *
	 * @return the number of totems dropped
	 */
	public static int enforce(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		Inventory inventory = player.getInventory();
		int remaining = excess(inventory);

		if (remaining <= 0) {
			return 0;
		}

		int dropped = 0;

		for (int slot = inventory.getContainerSize() - 1; slot >= 0 && remaining > 0; slot--) {
			ItemStack stack = inventory.getItem(slot);

			if (!isTotem(stack)) {
				continue;
			}

			int taken = Math.min(remaining, stack.getCount());
			remaining -= taken;
			dropped += taken;

			ItemStack removed = stack.copy();
			removed.setCount(taken);

			if (taken >= stack.getCount()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			} else {
				ItemStack kept = stack.copy();
				kept.setCount(stack.getCount() - taken);
				inventory.setItem(slot, kept);
			}

			player.drop(removed, false);
		}

		if (dropped > 0) {
			DragonMace.LOGGER.info("Dragons Pride dropped {} excess Totem(s) for {}.", dropped,
					player.getName().getString());
		}

		return dropped;
	}
}
