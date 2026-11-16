package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the curse on the new player object after a respawn.
 *
 * <p>Vanilla clears active effects on death and builds a fresh {@code ServerPlayer} for the respawn,
 * so the aura, the tablist tag, the Draconic Mace conversion and the totem limit all have to be
 * re-applied. {@code restoreFrom} runs after the new player has been placed in the level, which is
 * the first point where the effects can be written.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerRespawnMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void dragonmace$restoreCurse(ServerPlayer previous, boolean keepInventory, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		MinecraftServer server = self.level().getServer();

		if (server == null) {
			return;
		}

		DragonMaceCurse.onPlayerRespawn(server, self);
	}
}
