package com.lorenz.dragonmace;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dragons Wrath: converts the holder's maces into Draconic Maces and back again.
 *
 * <p>The conversion is tied to inventory membership, not to a method call. A mace is Draconic exactly
 * while it sits in the cursed player's inventory: it is converted when it enters, kept converted while
 * it is moved between slots, and reverted the moment it leaves through a drop, a container transfer or
 * a curse removal. Because the state is derived from where the stack is, a mace that comes back is
 * converted again and a mace that never returns stays a normal mace.
 *
 * <p>Only mace stacks are touched, and each one is replaced in place, so no unrelated inventory content
 * is moved, duplicated or deleted.
 */
public final class DragonMaceMaceConversion {
	/**
	 * Guards against re-entering a sweep.
	 *
	 * <p>Every conversion writes back through {@code Container.setItem}, which is itself hooked, so
	 * without this flag a single sweep would recursively sweep the inventory again. The flag is
	 * per-thread because the server runs inventory work on its own thread.
	 */
	private static final ThreadLocal<Boolean> SWEEPING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private DragonMaceMaceConversion() {
	}

	/** True when the stack is a mace that still has to be converted. */
	public static boolean isEligible(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.MACE) && !DragonMaceIdentity.isDraconicMace(stack);
	}

	/** True when the stack is a Draconic Mace that has to be reverted. */
	public static boolean isRevertible(ItemStack stack) {
		return !stack.isEmpty() && DragonMaceIdentity.isDraconicMace(stack);
	}

	/**
	 * Brings the player's inventory in line with their curse state.
	 *
	 * <p>A cursed holder has every eligible mace converted; anyone else has every Draconic Mace
	 * reverted. This is the single entry point every inventory hook calls, so the two directions can
	 * never disagree.
	 *
	 * @return the number of stacks changed
	 */
	public static int reconcile(ServerPlayer player, RegistryAccess registries) {
		if (player == null) {
			return 0;
		}

		if (Boolean.TRUE.equals(SWEEPING.get())) {
			return 0;
		}

		SWEEPING.set(Boolean.TRUE);

		try {
			boolean eligible = DragonMaceCurse.isActive(player.level().getServer(), player.getUUID());
			int changed = eligible ? convertInventory(player, registries) : revertInventory(player);

			// Every mace, Draconic or not, then follows the ownership rule: the holder keeps the
			// configured enchantments, everyone else keeps none.
			changed += DragonMaceEnchantments.reconcileContainer(player.getInventory(), eligible,
					registries, DragonMaceConfig.get());
			return changed;
		} finally {
			SWEEPING.set(Boolean.FALSE);
		}
	}

	/**
	 * Converts every eligible mace in the player's inventory.
	 *
	 * @return the number of stacks converted
	 */
	public static int convertInventory(ServerPlayer player, RegistryAccess registries) {
		if (player == null) {
			return 0;
		}

		int converted = convertContainer(player.getInventory(), registries);

		if (converted > 0) {
			DragonMace.LOGGER.info("Dragons Wrath converted {} mace stack(s) for {}.", converted,
					player.getName().getString());
		}

		return converted;
	}

	/**
	 * Converts every eligible mace in {@code container}, in place.
	 *
	 * <p>Each stack is replaced at its own slot, so nothing is moved, duplicated or deleted. Kept
	 * separate from {@link #convertInventory} so the conversion can be exercised against a real
	 * container without a live player.
	 *
	 * @return the number of stacks converted
	 */
	public static int convertContainer(Container container, RegistryAccess registries) {
		if (container == null) {
			return 0;
		}

		int converted = 0;

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (convertSlot(container, slot, registries)) {
				converted++;
			}
		}

		return converted;
	}

	/**
	 * Converts the stack in one slot, in place.
	 *
	 * @return true when the slot held an eligible mace and was converted
	 */
	public static boolean convertSlot(Container container, int slot, RegistryAccess registries) {
		if (container == null || slot < 0 || slot >= container.getContainerSize()) {
			return false;
		}

		ItemStack stack = container.getItem(slot);

		if (!isEligible(stack)) {
			return false;
		}

		container.setItem(slot, DragonMaceItems.createDraconicMaceStack(stack, registries));
		return true;
	}

	/**
	 * Reverts every Draconic Mace in the player's inventory.
	 *
	 * <p>The stack is never destroyed: it becomes the mace it was before the conversion, and a
	 * pre-existing Dragon Mace marker keeps the unique mace intact.
	 *
	 * @return the number of stacks reverted
	 */
	public static int revertInventory(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		int reverted = revertContainer(player.getInventory());

		if (reverted > 0) {
			DragonMace.LOGGER.info("Dragons Wrath reverted {} Draconic Mace stack(s) for {}.", reverted,
					player.getName().getString());
		}

		return reverted;
	}

	/**
	 * Reverts every Draconic Mace in {@code container}, in place.
	 *
	 * <p>Kept separate from {@link #revertInventory} so a curse removal can be exercised against a real
	 * container without a live player.
	 *
	 * @return the number of stacks reverted
	 */
	public static int revertContainer(Container container) {
		if (container == null) {
			return 0;
		}

		int reverted = 0;

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (revertSlot(container, slot)) {
				reverted++;
			}
		}

		return reverted;
	}

	/**
	 * Reverts the stack in one slot, in place.
	 *
	 * @return true when the slot held a Draconic Mace and was reverted
	 */
	public static boolean revertSlot(Container container, int slot) {
		if (container == null || slot < 0 || slot >= container.getContainerSize()) {
			return false;
		}

		ItemStack stack = container.getItem(slot);

		if (!isRevertible(stack)) {
			return false;
		}

		container.setItem(slot, DragonMaceItems.revertDraconicMace(stack));
		return true;
	}

	/**
	 * The stack as it must look outside the cursed player's inventory.
	 *
	 * <p>Used by the hooks that see a stack leave: a container write and an item entity. Returning the
	 * reverted stack instead of mutating in place keeps the caller's own write path intact, so the item
	 * is never dropped, duplicated or deleted by the reversion.
	 */
	public static ItemStack revertLeaving(ItemStack stack) {
		if (!DragonMaceEnchantments.isMace(stack)) {
			return stack;
		}

		if (isRevertible(stack)) {
			return DragonMaceItems.revertDraconicMace(stack);
		}

		if (stack.getEnchantments().isEmpty() && !stack.has(DataComponents.STORED_ENCHANTMENTS)) {
			return stack;
		}

		// A plain enchanted mace leaving the holder loses its enchantments too, on a copy so the
		// caller's own stack is never mutated behind its back.
		ItemStack copy = stack.copy();
		DragonMaceEnchantments.strip(copy);
		return copy;
	}
}
