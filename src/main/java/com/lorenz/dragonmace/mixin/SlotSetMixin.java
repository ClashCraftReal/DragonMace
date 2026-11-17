package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceMaceConversion;
import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks GUI-driven storage of the Dragon Mace outside the owner's inventory.
 *
 * <p>{@code Slot.set} writes through to {@code Container.setItem} and is reached by every menu
 * action: shift-click ({@code moveItemStackTo} to {@code setByPlayer} to {@code set}), drag and drop,
 * number-key swaps, offhand swaps and double-click collection. Slots backed by a player
 * {@code Inventory} are allowed so the mace can still move between the owner's own slots.
 */
@Mixin(Slot.class)
public class SlotSetMixin {
	/**
	 * Reverts a Draconic Mace that is written into any container that is not a player inventory.
	 *
	 * <p>This is the leaving path for chests, barrels, shulkers, hoppers, dispensers and every other
	 * menu-driven transfer: shift-click, drag and drop, number-key swaps and plain slot clicks all end
	 * in {@code Slot.set}. The stack is replaced before the write, so the container receives a normal
	 * mace and nothing is dropped or duplicated. A player inventory is left alone, which is what keeps a
	 * mace Draconic while it is only moved between the owner's own slots.
	 *
	 * <p>A {@link ResultContainer} is also left alone. Anvil and crafting result slots are staging, not
	 * storage: the stack is only parked there until the player takes it, and reverting it on write made
	 * the anvil hand back a stripped mace. The result is reconciled by the ownership rule instead.
	 */
	@ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true)
	private ItemStack dragonmace$revertLeavingMace(ItemStack stack) {
		Slot slot = (Slot) (Object) this;

		if (slot.container instanceof Inventory || slot.container instanceof ResultContainer) {
			return stack;
		}

		return DragonMaceMaceConversion.revertLeaving(stack);
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockStorage(ItemStack stack, CallbackInfo ci) {
		Slot slot = (Slot) (Object) this;

		if (DragonMaceProtection.blocksStorage(slot.container, stack)) {
			ci.cancel();
		}
	}
}
