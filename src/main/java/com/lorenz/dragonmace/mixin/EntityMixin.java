package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceRespawn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the Dragon Mace MISSING at the exact vanilla void-removal event.
 *
 * <p>In 1.21.11 {@code Entity.baseTick()} calls {@code checkBelowWorld()}, which calls
 * {@code onBelowWorld()} when the entity is below {@code minY - 64}; the base implementation then
 * discards the entity. Hooking {@code onBelowWorld()} is therefore specific to void destruction.
 * Pickup ({@code ItemEntity.playerTouch}), chunk unloading, dimension changes and entity merges never
 * pass through it, so none of them can mark the mace MISSING.
 */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "onBelowWorld", at = @At("HEAD"))
	private void dragonmace$markVoidDestruction(CallbackInfo ci) {
		if ((Object) this instanceof ItemEntity item) {
			DragonMaceRespawn.handleVoidDestruction(item);
		}
	}
}
