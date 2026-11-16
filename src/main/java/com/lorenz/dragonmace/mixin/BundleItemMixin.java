package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks bundles from holding the Dragon Mace.
 *
 * <p>{@code overrideStackedOnOther} and {@code overrideOtherStackedOnMe} are the two insertion
 * entry points called from {@code AbstractContainerMenu.doClick}. Returning {@code true} claims the
 * click was handled, so vanilla performs no insertion.
 */
@Mixin(BundleItem.class)
public class BundleItemMixin {
	@Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockBundleInsert(ItemStack bundle, Slot slot, ClickAction action, Player player,
			CallbackInfoReturnable<Boolean> cir) {
		if (DragonMaceProtection.blocksBundleInsert(slot.getItem())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockBundleInsertOnMe(ItemStack bundle, ItemStack other, Slot slot, ClickAction action,
			Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
		if (DragonMaceProtection.blocksBundleInsert(other)) {
			cir.setReturnValue(true);
		}
	}
}
