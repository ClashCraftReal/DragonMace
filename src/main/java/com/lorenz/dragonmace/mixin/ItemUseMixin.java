package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceIdentity;
import com.lorenz.dragonmace.DragonMaceItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consumes the Very Heavy Core on a right-click.
 *
 * <p>The core is {@code minecraft:paper}, so there is no custom item class to override. Injecting at
 * the head of {@code Item.use} is the single entry point for a right-click in the air, and it runs
 * before any block interaction, so the consumption works whether the player clicks air or a block.
 * Paper is not a {@code BlockItem}, so no placement path exists to guard.
 */
@Mixin(Item.class)
public class ItemUseMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void dragonmace$consumeVeryHeavyCore(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = player.getItemInHand(hand);

		if (!DragonMaceIdentity.isVeryHeavyCore(stack)) {
			return;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			// The client must not predict the consumption; the server result is authoritative.
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		cir.setReturnValue(DragonMaceItems.consumeVeryHeavyCore(serverPlayer, stack));
	}
}
