package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks dispensers and droppers from accepting the Dragon Mace.
 *
 * <p>{@code DispenserBlockEntity.insertItem} is the insertion point used by
 * {@code DefaultDispenseItemBehavior.addToInventoryOrDispense}, which has no menu slot and therefore
 * bypasses the slot hook.
 */
@Mixin(DispenserBlockEntity.class)
public class DispenserBlockEntityMixin {
	@Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockInsert(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (DragonMaceProtection.isProtectedStack(stack)) {
			cir.setReturnValue(stack);
		}
	}
}
