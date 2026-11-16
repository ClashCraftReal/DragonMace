package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockStorage(ItemStack stack, CallbackInfo ci) {
		Slot slot = (Slot) (Object) this;

		if (DragonMaceProtection.blocksStorage(slot.container, stack)) {
			ci.cancel();
		}
	}
}
