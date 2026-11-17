package com.lorenz.dragonmace.mixin.client;

import com.lorenz.dragonmace.client.DragonMaceClient;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops Shift + F from using the offhand item.
 *
 * <p>The enchantment shortcut is bound to {@code F}, the same key vanilla binds to the offhand swap.
 * Both mappings therefore fire on one press, and the swap ran before the menu request was sent, so the
 * offhand item was consumed, placed or swapped. This redirect drains the swap binding without acting on
 * it while the shortcut is held, leaving plain {@code F} untouched.
 */
@Mixin(Minecraft.class)
public class MinecraftKeybindMixin {
	@Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
	private boolean dragonmace$suppressOffhandSwap(KeyMapping mapping) {
		Minecraft client = Minecraft.getInstance();

		if (client != null && mapping == client.options.keySwapOffhand
				&& DragonMaceClient.isMenuShortcutDown()) {
			// Drain the queued press so it cannot fire on a later tick, then report no action.
			mapping.consumeClick();
			return false;
		}

		return mapping.consumeClick();
	}
}
