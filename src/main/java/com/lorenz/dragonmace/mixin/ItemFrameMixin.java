package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks item frames and glow item frames from holding the Dragon Mace.
 *
 * <p>Both frame types share {@code ItemFrame.setItem(ItemStack, boolean)}, which is the single write
 * path for placing an item into a frame.
 */
@Mixin(ItemFrame.class)
public class ItemFrameMixin {
	@Inject(method = "setItem(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockFrame(ItemStack stack, boolean updateNeighbours, CallbackInfo ci) {
		if (DragonMaceProtection.isProtectedStack(stack)) {
			ci.cancel();
		}
	}
}
