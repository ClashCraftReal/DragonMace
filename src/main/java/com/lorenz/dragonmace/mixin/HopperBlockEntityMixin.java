package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks hopper and hopper-minecart transfers of the Dragon Mace.
 *
 * <p>{@code HopperBlockEntity.addItem(Container, Container, ItemStack, Direction)} is the shared
 * transfer used by both the eject and suck paths, so one hook covers pushing the mace into a hopper
 * and pulling it out of any container.
 */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
	@Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), cancellable = true)
	private static void dragonmace$blockHopperTransfer(Container source, Container destination, ItemStack stack,
			Direction direction, CallbackInfoReturnable<ItemStack> cir) {
		if (DragonMaceProtection.isProtectedStack(stack)) {
			cir.setReturnValue(stack);
		}
	}

	@Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
			at = @At("HEAD"), cancellable = true)
	private static void dragonmace$blockHopperPickup(Container container, ItemEntity entity,
			CallbackInfoReturnable<Boolean> cir) {
		if (DragonMaceProtection.isProtectedStack(entity.getItem())) {
			cir.setReturnValue(false);
		}
	}
}
