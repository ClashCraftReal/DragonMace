package com.lorenz.dragonmace;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
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
	 * True when inserting {@code incoming} into {@code inventory} would break Dragons Pride.
	 *
	 * <p>Used by the click and shift-click guards to refuse the transfer <em>before</em> anything is
	 * moved, so a rejected transfer leaves the source stack exactly where it was. Nothing is ever
	 * dropped or destroyed to satisfy the limit.
	 */
	public static boolean blocksInsert(Inventory inventory, ItemStack incoming) {
		return !allowsAdd(inventory, incoming);
	}

	/**
	 * Curse-aware insertion decision.
	 *
	 * <p>Dragons Pride only exists for the current curse holder, so a player without the curse is never
	 * refused. This is the single decision the click guard consults, which keeps the gate and the
	 * counting rule in one place instead of duplicating the owner check in a mixin.
	 */
	public static boolean blocksInsert(UUID owner, UUID player, int carried, ItemStack incoming) {
		if (!appliesTo(owner, player)) {
			return false;
		}

		return !allowsAdd(carried, incoming);
	}

	/**
	 * True when a shift-click of {@code stack} into {@code inventory} would break Dragons Pride.
	 *
	 * <p>The source stack is skipped by identity, so a shift-click that starts inside the player
	 * inventory is an internal move and is never refused.
	 */
	public static boolean blocksQuickMove(Inventory inventory, ItemStack stack) {
		if (inventory == null || !isTotem(stack)) {
			return false;
		}

		int carried = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack existing = inventory.getItem(slot);

			if (existing == stack) {
				continue;
			}

			if (isTotem(existing)) {
				carried += existing.getCount();
			}
		}

		return !allowsAdd(carried, stack);
	}

	/**
	 * Plans which totems must be dropped, honouring the priority order.
	 *
	 * <p>The offhand totem is always kept. The remaining budget is then spent on the inventory slots in
	 * ascending order, so the lowest slot keeps its totem and every later slot drops its excess. Pure,
	 * so the whole priority table can be tested without a live player.
	 *
	 * @param offhandCount    totems in the offhand slot
	 * @param inventoryCounts totem counts for the main inventory and hotbar, in slot order
	 * @return the number of totems to drop from each inventory slot, in the same order
	 */
	public static int[] planDrops(int offhandCount, int[] inventoryCounts) {
		int[] drops = new int[inventoryCounts.length];
		int budget = MAX_TOTEMS - Math.min(Math.max(offhandCount, 0), MAX_TOTEMS);

		for (int index = 0; index < inventoryCounts.length; index++) {
			int count = Math.max(inventoryCounts[index], 0);
			int keep = Math.min(count, budget);
			budget -= keep;
			drops[index] = count - keep;
		}

		return drops;
	}

	/** Guards against re-entering enforcement from the inventory writes it performs itself. */
	private static final ThreadLocal<Boolean> ENFORCING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/**
	 * Drops every totem beyond the two the holder may carry.
	 *
	 * <p>The offhand totem is never touched. One further totem is kept from the lowest inventory slot
	 * that holds one, and every remaining totem is dropped into the world with {@code Player.drop}, so
	 * nothing is deleted or duplicated and a dropped stack keeps its count. Re-entrancy is blocked
	 * because the slot writes below run through the same inventory hooks that call this method.
	 *
	 * @return how many totems were dropped
	 */
	public static int enforce(ServerPlayer player) {
		if (player == null || ENFORCING.get()) {
			return 0;
		}

		// Dragons Pride only exists while the curse is held. Without this gate the limit stayed active
		// after a withdrawal, a removal or a transfer away, and a player who legitimately carried more
		// than two totems had them dropped for no reason.
		MinecraftServer server = player.level().getServer();

		if (server == null || !appliesTo(DragonMaceCurse.activeOwner(server), player.getUUID())) {
			return 0;
		}

		Inventory inventory = player.getInventory();

		if (countTotems(inventory) <= MAX_TOTEMS) {
			return 0;
		}

		ENFORCING.set(Boolean.TRUE);

		try {
			int slots = Inventory.SELECTION_SIZE + 27;
			int[] counts = new int[slots];

			for (int slot = 0; slot < slots; slot++) {
				counts[slot] = count(inventory.getItem(slot));
			}

			int[] drops = planDrops(count(inventory.getItem(Inventory.SLOT_OFFHAND)), counts);
			int dropped = 0;

			for (int slot = 0; slot < slots; slot++) {
				if (drops[slot] <= 0) {
					continue;
				}

				ItemStack stack = inventory.getItem(slot);
				ItemStack excess = stack.copyWithCount(drops[slot]);
				stack.shrink(drops[slot]);

				if (stack.isEmpty()) {
					inventory.setItem(slot, ItemStack.EMPTY);
				}

				player.drop(excess, false);
				dropped += drops[slot];
			}

			return dropped;
		} finally {
			ENFORCING.set(Boolean.FALSE);
		}
	}

	private static int count(ItemStack stack) {
		return isTotem(stack) ? stack.getCount() : 0;
	}

	/**
	 * True when Dragons Pride applies to {@code player}.
	 *
	 * <p>The limit exists only for the player who currently owns the curse. Pure, so the whole lifecycle
	 * can be tested without a server: a withdrawal, a removal or a transfer away clears the owner, and
	 * this returns {@code false} immediately afterwards, which is what lifts the restriction.
	 */
	public static boolean appliesTo(UUID owner, UUID player) {
		return owner != null && owner.equals(player);
	}
}
