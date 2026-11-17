package com.lorenz.dragonmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DragonMace implements ModInitializer {
	public static final String MOD_ID = "dragon-mace";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Loaded eagerly so the default file exists for operators to edit before the first respawn.
		DragonMaceConfig.get();

		// Attack runes. The callback is server-side only: onAttack re-checks the curse and the main
		// hand, so a client cannot trigger a rune it does not own.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
				DragonMaceRuneEffects.onAttack(serverPlayer, entity);
			}

			return InteractionResult.PASS;
		});

		// Deferred mace conversions. One cheap pass per tick, and only while something is queued.
		ServerTickEvents.END_SERVER_TICK.register(DragonMaceMaceConversion::tick);

		// Aura runes. One cheap pass every ten ticks for the single curse holder, recomputed from live
		// state so the aura stops as soon as the curse or the last Draconic Mace is gone.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % DragonMaceRuneEffects.AURA_INTERVAL_TICKS == 0) {
				DragonMaceRuneEffects.tickAura(server);
			}
		});

		LOGGER.info("Dragon Mace initialized (server-side only).");
	}
}
