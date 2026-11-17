package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks shelves from accepting a protected stack.
 *
 * <p>A shelf is filled by {@code ShelfBlock.useItemOn}, which calls
 * {@link ShelfBlockEntity#swapItemNoUpdate(int, ItemStack)} directly. No menu slot and no
 * {@code Container.setItem} is involved, so the slot-level guards never saw shelf insertion.
 *
 * <p>The hook returns the incoming stack unchanged. The caller writes that return value back into the
 * player's selected slot, so the egg stays exactly where it was and the shelf keeps its own item.
 */
@Mixin(ShelfBlockEntity.class)
public class ShelfBlockEntityMixin {
	@Inject(method = "swapItemNoUpdate", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockShelfInsert(int slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (DragonMaceProtection.blocksShelfInsert(stack)) {
			cir.setReturnValue(stack);
		}
	}
}
