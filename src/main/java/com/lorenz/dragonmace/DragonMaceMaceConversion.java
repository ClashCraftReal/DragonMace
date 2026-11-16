package com.lorenz.dragonmace;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dragons Wrath: converts the holder's maces into Draconic Maces and back again.
 *
 * <p>Only mace stacks are touched, and each one is replaced in place, so no unrelated inventory
 * content is moved, duplicated or deleted. The conversion is written into the item's data components,
 * which is why a converted mace stays converted after dropping, picking up, reconnecting and
 * restarting.
 */
public final class DragonMaceMaceConversion {
	private DragonMaceMaceConversion() {
	}

	/** True when the stack is a mace that still has to be converted. */
	public static boolean isEligible(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.MACE) && !DragonMaceIdentity.isDraconicMace(stack);
	}

	/**
	 * Converts every eligible mace in the player's inventory.
	 *
	 * @return the number of stacks converted
	 */
	public static int convertInventory(ServerPlayer player, RegistryAccess registries) {
		if (player == null || registries == null) {
			return 0;
		}

		Inventory inventory = player.getInventory();
		int converted = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!isEligible(stack)) {
				continue;
			}

			inventory.setItem(slot, DragonMaceItems.createDraconicMaceStack(stack, registries));
			converted++;
		}

		if (converted > 0) {
			DragonMace.LOGGER.info("Dragons Wrath converted {} mace stack(s) for {}.", converted,
					player.getName().getString());
		}

		return converted;
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

		Inventory inventory = player.getInventory();
		int reverted = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!DragonMaceIdentity.isDraconicMace(stack)) {
				continue;
			}

			inventory.setItem(slot, DragonMaceItems.revertDraconicMace(stack));
			reverted++;
		}

		if (reverted > 0) {
			DragonMace.LOGGER.info("Dragons Wrath reverted {} Draconic Mace stack(s) for {}.", reverted,
					player.getName().getString());
		}

		return reverted;
	}
}
