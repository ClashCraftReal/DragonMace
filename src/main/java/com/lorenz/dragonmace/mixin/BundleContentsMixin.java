package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks every bundle insertion path at the funnel both click overrides use.
 *
 * <p>{@code BundleItem.overrideStackedOnOther} and {@code overrideOtherStackedOnMe} both build a
 * {@link BundleContents.Mutable} and call {@code tryInsert} or {@code tryTransfer}. Guarding those two
 * methods covers the click overrides and any other caller, and returning {@code 0} means nothing was
 * inserted, so the egg is never consumed and the bundle is never rewritten.
 */
@Mixin(BundleContents.Mutable.class)
public class BundleContentsMixin {
	@Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockBundleTryInsert(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (DragonMaceProtection.blocksBundleInsert(stack)) {
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "tryTransfer", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockBundleTryTransfer(Slot slot, Player player, CallbackInfoReturnable<Integer> cir) {
		if (slot != null && DragonMaceProtection.blocksBundleInsert(slot.getItem())) {
			cir.setReturnValue(0);
		}
	}
}
