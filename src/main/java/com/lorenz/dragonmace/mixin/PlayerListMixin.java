package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceRecipeUnlock;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores or cleans up the curse when a player joins.
 *
 * <p>{@code placeNewPlayer} runs after the player has been added to the level and to the player list,
 * so the aura can be applied and the tablist tag can be broadcast to everyone including the joining
 * player. A player who does not hold the curse has any stale aura and tag removed here.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Inject(method = "placeNewPlayer", at = @At("TAIL"))
	private void dragonmace$restoreCurseOnJoin(Connection connection, ServerPlayer player,
			CommonListenerCookie cookie, CallbackInfo ci) {
		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return;
		}

		DragonMaceCurse.onPlayerJoin(server, player);
		DragonMaceRecipeUnlock.unlockIfHoldingEgg(player);
	}
}
