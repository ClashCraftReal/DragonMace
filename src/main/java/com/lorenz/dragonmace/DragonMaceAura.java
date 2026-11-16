package com.lorenz.dragonmace;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The permanent Dragons Aura of the Curse of the Dragons holder.
 *
 * <p>The aura is a single infinite {@code minecraft:glowing} effect. It is applied with
 * {@code ambient=false, visible=false, showIcon=false} so it never spams the effect HUD, and it is
 * re-applied on join and respawn because vanilla clears active effects on death.
 *
 * <p>The outline colour of a glowing entity is chosen by the client from the entity's scoreboard team
 * colour, not from the effect, so a server-side-only mod cannot tint it purple without rewriting the
 * player's scoreboard identity. That limitation is recorded in {@code PROJECT_STATUS.md}.
 */
public final class DragonMaceAura {
	private static final Holder<MobEffect> GLOWING = MobEffects.GLOWING;

	private DragonMaceAura() {
	}

	/** Applies the infinite glowing aura. */
	public static void apply(ServerPlayer player) {
		if (player == null) {
			return;
		}

		MobEffectInstance current = player.getEffect(GLOWING);

		// Re-adding an identical infinite effect every join would reset its duration for no reason.
		if (current != null && current.isInfiniteDuration() && current.getAmplifier() == 0) {
			return;
		}

		player.addEffect(new MobEffectInstance(GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
	}

	/** Removes the aura from a player who no longer holds the curse. */
	public static void clear(ServerPlayer player) {
		if (player == null) {
			return;
		}

		player.removeEffect(GLOWING);
	}

	/**
	 * Removes a leftover aura from a player who does not hold the curse.
	 *
	 * <p>Only the exact aura instance is removed, so a glowing effect from a beacon, a spectral arrow
	 * or another mod is left alone.
	 */
	public static void clearStale(ServerPlayer player) {
		if (player == null) {
			return;
		}

		MobEffectInstance current = player.getEffect(GLOWING);

		if (current != null && current.isInfiniteDuration() && current.getAmplifier() == 0) {
			player.removeEffect(GLOWING);
		}
	}
}
