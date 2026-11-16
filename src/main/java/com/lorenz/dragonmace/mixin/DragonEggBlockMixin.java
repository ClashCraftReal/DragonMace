package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonEggProtection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the dragon egg teleport atomic on the server.
 *
 * <p>Vanilla picks a random destination and then writes twice: it sets the egg at the destination
 * and afterwards removes the origin block. A zero offset therefore sets and immediately removes the
 * same position, destroying the egg. This hook keeps vanilla's distribution and constraints but
 * skips a self-teleport and only removes the origin once the destination write succeeded.
 *
 * <p>The client branch is left to vanilla so the portal particle effect is unchanged.
 */
@Mixin(DragonEggBlock.class)
public class DragonEggBlockMixin {
	@Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
	private void dragonmace$safeTeleport(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (level.isClientSide()) {
			return;
		}

		BlockPos destination = DragonEggProtection.findTeleportDestination(pos, level.getRandom(),
				candidate -> level.getBlockState(candidate).isAir(),
				candidate -> level.getWorldBorder().isWithinBounds(candidate) && !level.isOutsideBuildHeight(candidate));

		ci.cancel();

		if (destination == null || DragonEggProtection.isSelfTeleport(pos, destination)) {
			return;
		}

		if (level.setBlock(destination, state, 2)) {
			level.removeBlock(pos, false);
		}
	}
}
