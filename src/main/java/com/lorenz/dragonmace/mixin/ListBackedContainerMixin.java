package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ListBackedContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks automated insertion of a protected stack into a list-backed block container.
 *
 * <p>{@link ListBackedContainer} is the storage implementation behind shelves. Its {@code setItem},
 * {@code setItemNoUpdate} and {@code canPlaceItem} defaults write straight into the backing list, so a
 * hopper, a dropper or any other {@code Container} caller bypassed the slot guards. Rejecting
 * {@code canPlaceItem} stops the transfer at the decision point, before the source is touched, and the
 * two setters are refused as a second net.
 */
@Mixin(ListBackedContainer.class)
public interface ListBackedContainerMixin {
	@Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
	private void dragonmace$refuseProtectedPlacement(int slot, ItemStack stack,
			CallbackInfoReturnable<Boolean> cir) {
		if (DragonMaceProtection.blocksShelfInsert(stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
	private void dragonmace$refuseProtectedSet(int slot, ItemStack stack, CallbackInfo ci) {
		if (DragonMaceProtection.blocksShelfInsert(stack)) {
			ci.cancel();
		}
	}

	@Inject(method = "setItemNoUpdate", at = @At("HEAD"), cancellable = true)
	private void dragonmace$refuseProtectedSetNoUpdate(int slot, ItemStack stack, CallbackInfo ci) {
		if (DragonMaceProtection.blocksShelfInsert(stack)) {
			ci.cancel();
		}
	}
}
