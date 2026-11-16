package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceTabList;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the custom tablist tag for the Curse of the Dragons holder.
 *
 * <p>In 1.21.11 {@code ServerPlayer.getTabListDisplayName()} is a plain {@code return null} stub, and
 * {@code ClientboundPlayerInfoUpdatePacket.Entry} reads its display name from exactly that method.
 * Overriding the return value here is therefore the single point that both the initial tab list
 * population and every later {@code UPDATE_DISPLAY_NAME} broadcast go through.
 *
 * <p>The player's game profile, username, chat name and scoreboard identity are never modified.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerTabListMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void dragonmace$curseTag(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		MinecraftServer server = self.level().getServer();

		if (server == null) {
			return;
		}

		if (DragonMaceCurse.isActive(server, self.getUUID())) {
			cir.setReturnValue(DragonMaceTabList.displayName(self));
		}
	}
}
