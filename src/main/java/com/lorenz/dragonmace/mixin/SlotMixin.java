package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceIdentity;
import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks taking a Dragon Mace out of a crafting result slot once the world already has one.
 *
 * <p>{@link Slot#mayPickup(Player)} is the single gate every take path goes through (pickup, swap,
 * shift-click, drag), and it runs before the stack is moved anywhere, so this also closes the window
 * where two players already hold a computed result at the same time.
 */
@Mixin(Slot.class)
public class SlotMixin {
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void dragonmace$blockSecondDragonMace(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ResultSlot)) {
			return;
		}

		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return;
		}

		ItemStack stack = ((Slot) (Object) this).getItem();

		if (!DragonMaceIdentity.isDragonMace(stack)) {
			return;
		}

		if (DragonMaceWorldData.get(server).isCrafted()) {
			cir.setReturnValue(false);
		}
	}
}
