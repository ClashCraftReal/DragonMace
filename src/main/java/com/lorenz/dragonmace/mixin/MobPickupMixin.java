package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops baby piglins from picking up the Dragon Egg.
 *
 * <p>Baby piglins grab items through {@code Mob.pickUpItem}, which is not a container and therefore
 * bypasses every slot, hopper and dispenser guard. Cancelling at the head of the shared mob pickup
 * keeps the egg on the ground where it can still be collected by a player.
 */
@Mixin(Mob.class)
public class MobPickupMixin {
	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockPiglinEggPickup(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
		if (!((Object) this instanceof Piglin piglin) || !piglin.isBaby()) {
			return;
		}

		if (DragonMaceProtection.isProtectedStack(entity.getItem())) {
			ci.cancel();
		}
	}
}
