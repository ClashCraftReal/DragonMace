package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceMenus;

import net.minecraft.core.registries.BuiltInRegistries;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the mod's menu type before the registries freeze.
 *
 * <p>{@code BuiltInRegistries.bootStrap()} runs during Minecraft's static initialisation, before mod
 * initialisers, and freezes the registries as it finishes. Registering from {@code onInitialize}
 * therefore throws {@code This registry can't create intrusive holders}, so the registration is done
 * at the head of this method instead.
 */
@Mixin(BuiltInRegistries.class)
public class BootstrapMixin {
	@Inject(method = "bootStrap", at = @At("HEAD"))
	private static void dragonmace$registerMenus(CallbackInfo ci) {
		DragonMaceMenus.register();
	}
}
