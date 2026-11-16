package com.lorenz.dragonmace.mixin;

import java.util.function.BooleanSupplier;

import com.lorenz.dragonmace.DragonMaceRespawn;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the reconciliation pass at the end of the server tick.
 *
 * <p>This pass may adopt a mace that is still loaded, but it never respawns a MISSING mace. Void
 * destruction deliberately waits for an operator to run {@code /spawndragonmace}.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Inject(method = "tickServer", at = @At("TAIL"))
	private void dragonmace$recoverMissingMace(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		DragonMaceRespawn.tick((MinecraftServer) (Object) this);
	}
}
