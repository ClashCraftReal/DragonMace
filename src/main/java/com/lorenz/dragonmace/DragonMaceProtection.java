package com.lorenz.dragonmace;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Pure rules for where the Dragon Mace may and may not live.
 *
 * <p>The mace is bound to its owner, so it may only sit in that owner's player inventory. Every
 * other container is refused, and the mixins only supply the live objects.
 */
public final class DragonMaceProtection {
	private DragonMaceProtection() {
	}

	/**
	 * True when the stack is a special item that must never enter a container.
	 *
	 * <p>The Dragon Mace and the Dragon Egg share the same storage rules. Both are identified by their
	 * persistent item identity, never by a display name, so a renamed stack is still protected.
	 */
	public static boolean isProtectedStack(ItemStack stack) {
		return DragonMaceIdentity.isDragonMace(stack) || DragonMaceIdentity.isDragonEgg(stack);
	}

	/**
	 * Only a player inventory is an allowed home. Chests, barrels, shulkers, hoppers, dispensers,
	 * droppers and ender chests are all {@code Container} implementations that are not
	 * {@link Inventory}, so they are refused by the same check.
	 */
	public static boolean isAllowedContainer(Container container) {
		return container instanceof Inventory;
	}

	/** True when writing {@code stack} into {@code container} must be refused. */
	public static boolean blocksStorage(Container container, ItemStack stack) {
		return isProtectedStack(stack) && !isAllowedContainer(container);
	}

	/**
	 * True when writing {@code stack} into the slot's backing container must be refused.
	 *
	 * <p>Called from the menu transaction entry point, before any source stack is split or cleared.
	 */
	public static boolean blocksSlotStorage(Slot slot, ItemStack stack) {
		return blocksStorage(slot.container, stack);
	}

	/**
	 * True when a quick-move range contains a destination that may not receive the mace.
	 *
	 * <p>The whole move is rejected when any destination slot is protected. This is checked at the
	 * head of {@code moveItemStackTo}, which is the primitive every menu uses for shift-click, so the
	 * source stack is still intact when the move is refused.
	 */
	public static boolean blocksQuickMove(List<Slot> slots, int startInclusive, int endExclusive, ItemStack stack) {
		if (!isProtectedStack(stack)) {
			return false;
		}

		int start = Math.max(0, startInclusive);
		int end = Math.min(slots.size(), endExclusive);

		for (int index = start; index < end; index++) {
			if (blocksSlotStorage(slots.get(index), stack)) {
				return true;
			}
		}

		return false;
	}

	/** True when a bundle must refuse {@code stack} in either insertion direction. */
	public static boolean blocksBundleInsert(ItemStack stack) {
		return isProtectedStack(stack);
	}

	/**
	 * The permanent ground protections that apply to a dropped Dragon Mace.
	 *
	 * <p>Kept as a pure value so the protection decision can be tested without constructing a live
	 * entity; {@link #protectItemEntity(ItemEntity)} is the only place that applies it.
	 */
	public record GroundProtection(boolean unlimitedLifetime, boolean invulnerable) {
	}

	/** Pure protection decision for a stack that is about to become an item entity. */
	public static GroundProtection groundProtection(ItemStack stack) {
		boolean protectedStack = isProtectedStack(stack);
		return new GroundProtection(protectedStack, protectedStack);
	}

	/**
	 * Applies the permanent ground protections to a Dragon Mace item entity.
	 *
	 * <p>Both flags are saved with the entity, so a dropped mace neither despawns nor burns in fire
	 * or lava. Called for every item entity that receives a Dragon Mace stack, including entities
	 * loaded from disk, so old maces gain the protection too.
	 */
	public static void protectItemEntity(ItemEntity entity) {
		GroundProtection protection = groundProtection(entity.getItem());

		if (protection.unlimitedLifetime()) {
			entity.setUnlimitedLifetime();
		}

		if (protection.invulnerable()) {
			entity.setInvulnerable(true);
		}
	}
}
