package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMace;
import com.lorenz.dragonmace.DragonMaceIdentity;
import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the world uniqueness flag at the moment a Dragon Mace is actually taken out of a crafting
 * result slot, which is the point where the craft has succeeded.
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void dragonmace$markCrafted(Player player, ItemStack stack, CallbackInfo ci) {
		if (!DragonMaceIdentity.isDragonMace(stack)) {
			return;
		}

		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return;
		}

		if (DragonMaceWorldData.get(server).markCrafted()) {
			DragonMace.LOGGER.info("Dragon Mace crafted by {}; world uniqueness flag set.", player.getName().getString());
		}
	}
}
