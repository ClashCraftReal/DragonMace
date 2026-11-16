package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonEggProtection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops a falling dragon egg from dropping a duplicate item when it lands on an existing dragon egg.
 *
 * <p>In {@code FallingBlockEntity.tick()} the "cannot place here" branch discards the entity and
 * then calls {@code spawnAtLocation} (the second of the three call sites). When the landing position
 * already holds a dragon egg the block survives, so that drop would duplicate the egg. Only that
 * drop is suppressed; the entity is still discarded by vanilla and every other falling block is
 * untouched.
 */
@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;",
					ordinal = 1
			)
	)
	private ItemEntity dragonmace$suppressDuplicateEggDrop(FallingBlockEntity self, ServerLevel level, ItemLike item) {
		if (DragonEggProtection.isDuplicateLanding(level.getBlockState(self.blockPosition()), self.getBlockState())) {
			return null;
		}

		return self.spawnAtLocation(level, item);
	}
}
