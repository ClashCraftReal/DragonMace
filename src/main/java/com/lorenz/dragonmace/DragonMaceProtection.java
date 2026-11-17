package com.lorenz.dragonmace;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
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
	 * <p>The Dragon Mace, the Dragon Egg and the Very Heavy Core share the same storage rules. All three
	 * are identified by their persistent item identity, never by a display name or a base item type, so a
	 * renamed stack is still protected and ordinary paper is never mistaken for a core.
	 */
	public static boolean isProtectedStack(ItemStack stack) {
		return DragonMaceIdentity.isDragonMace(stack) || DragonMaceIdentity.isDragonEgg(stack)
				|| DragonMaceIdentity.isVeryHeavyCore(stack);
	}

	/**
	 * Only a player inventory or a crafting grid is an allowed home. Chests, barrels, shulkers, hoppers,
	 * dispensers, droppers and ender chests are all {@code Container} implementations that are neither,
	 * so they are refused by the same check.
	 *
	 * <p>Crafting grids are allowed because the Dragon Egg is a real ingredient of the Very Heavy Core
	 * recipe. A crafting grid is backed by a {@code TransientCraftingContainer}, which implements
	 * {@link CraftingContainer} but not {@link Inventory}, so refusing every non-inventory container
	 * also refused the egg in the crafting grid and made the recipe uncraftable. Allowing
	 * {@code CraftingContainer} covers both the 2x2 inventory grid and the 3x3 crafting table grid
	 * without opening up any storage block.
	 */
	public static boolean isAllowedContainer(Container container) {
		return container instanceof Inventory || container instanceof CraftingContainer
				|| container instanceof ResultContainer;
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
	 * True when a shelf must refuse {@code stack}.
	 *
	 * <p>A shelf is storage, so it follows the same rule as a chest. It is checked separately because
	 * a shelf is filled by {@code ShelfBlockEntity.swapItemNoUpdate} and never by a menu slot, so the
	 * slot-level guards never see it.
	 */
	public static boolean blocksShelfInsert(ItemStack stack) {
		return isProtectedStack(stack);
	}

	/**
	 * True when a number-key or offhand swap would write a protected stack into {@code slot}.
	 *
	 * <p>{@code button} is the hotbar index {@code 0-8} or {@link Inventory#SLOT_OFFHAND} for the
	 * offhand swap. Vanilla clears the source inventory slot before it writes the destination, so this
	 * decision has to be made before the click runs; the mixin cancels the whole click on {@code true}.
	 */
	public static boolean blocksSwapInto(Slot slot, int button, ItemStack swappedIn) {
		if (button < 0 || (button >= Inventory.SELECTION_SIZE && button != Inventory.SLOT_OFFHAND)) {
			return false;
		}

		return blocksSlotStorage(slot, swappedIn);
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
		// The mace, the egg and the Very Heavy Core are all permanent ground items: they never despawn
		// and never burn. The core is matched by its custom identity, so ordinary paper keeps vanilla
		// behaviour. Void destruction is not damage-based, so it still applies to all three.
		boolean protectedStack = DragonMaceIdentity.isDragonMace(stack) || DragonMaceIdentity.isDragonEgg(stack)
				|| DragonMaceIdentity.isVeryHeavyCore(stack);
		return new GroundProtection(protectedStack, protectedStack);
	}

	/**
	 * True when a dropped stack must be immune to fire and lava.
	 *
	 * <p>Separate from {@link #groundProtection} because it is also consulted by
	 * {@code ItemEntity.fireImmune()}, which is the gate {@code lavaHurt} uses before it applies damage
	 * and sets the entity alight. Invulnerability already blocks the damage, but the entity would still
	 * catch fire and play the lava hurt sound without this.
	 */
	public static boolean isFireImmune(ItemStack stack) {
		return groundProtection(stack).invulnerable();
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
