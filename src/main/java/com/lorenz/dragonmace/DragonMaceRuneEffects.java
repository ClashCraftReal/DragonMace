package com.lorenz.dragonmace;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The server-side gameplay effects of the three rune categories.
 *
 * <p>Every visual is a vanilla particle sent from the server, so nothing is client-only and every
 * nearby player sees the same effect. The particle and potion choices are pure functions of the rune, so
 * the whole mapping is testable without a world.
 *
 * <p>Nothing here is destructive: no explosions, no block changes, no lightning. The strongest effect is
 * a short potion instance on the target.
 */
public final class DragonMaceRuneEffects {
	/** How often the aura pass runs, in server ticks. */
	public static final int AURA_INTERVAL_TICKS = 10;

	/** A potion instance one rune applies to its target. */
	public record AppliedEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) {
	}

	private DragonMaceRuneEffects() {
	}

	// ------------------------------------------------------------------ pure mapping

	/** The particle a Kill rune bursts on the victim. */
	public static ParticleOptions killParticle(DragonMaceRune rune) {
		ParticleOptions particle = switch (DragonMaceRune.byIdOrDefault(rune == null ? null : rune.id())) {
			case DRACONIC -> ParticleTypes.PORTAL;
			case SOULFLAME -> ParticleTypes.SOUL_FIRE_FLAME;
			case TEMPEST -> ParticleTypes.ELECTRIC_SPARK;
			case ABYSSAL -> ParticleTypes.SCULK_SOUL;
			case WARDEN -> ParticleTypes.SOUL;
		};

		return particle;
	}

	/** The particle an Attack rune bursts on the target. */
	public static ParticleOptions attackParticle(DragonMaceRune rune) {
		return killParticle(rune);
	}

	/** The particle an Aura rune trails around its holder. */
	public static ParticleOptions auraParticle(DragonMaceRune rune) {
		return killParticle(rune);
	}

	/**
	 * The potion an Attack rune applies to its target, if any.
	 *
	 * <p>Draconic and Tempest are particle-only, so they return empty rather than a no-op instance.
	 */
	public static Optional<AppliedEffect> attackEffect(DragonMaceRune rune) {
		return switch (DragonMaceRune.byIdOrDefault(rune == null ? null : rune.id())) {
			case ABYSSAL -> Optional.of(new AppliedEffect(MobEffects.DARKNESS, 100, 0));
			case WARDEN -> Optional.of(new AppliedEffect(MobEffects.SLOWNESS, 60, 1));
			// Soulflame ignites instead of applying a potion; Draconic and Tempest are particle-only.
			case DRACONIC, SOULFLAME, TEMPEST -> Optional.empty();
		};
	}

	/** True when an Attack rune sets its target alight. */
	public static boolean ignitesTarget(DragonMaceRune rune) {
		return rune == DragonMaceRune.SOULFLAME;
	}

	/** True when an Attack rune knocks its target back. */
	public static boolean knocksBack(DragonMaceRune rune) {
		return rune == DragonMaceRune.TEMPEST;
	}

	// ------------------------------------------------------------------ live effects

	/**
	 * Applies the Kill rune of {@code killer} to a defeated {@code victim}.
	 *
	 * <p>Particle-only: a kill rune never adds damage or drops, so it cannot change the outcome of a
	 * fight that has already been decided.
	 */
	public static void onKill(ServerPlayer killer, LivingEntity victim, DragonMaceRune rune) {
		if (killer == null || victim == null || !(victim.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();

		if (server == null || !DragonMaceProgression.isEnabled(server, killer.getUUID(),
				DragonMaceRuneCategory.KILL)) {
			return;
		}

		level.sendParticles(killParticle(rune), victim.getX(), victim.getY() + 1.0, victim.getZ(),
				24, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/**
	 * Applies the Attack rune of {@code attacker} to {@code target}.
	 *
	 * <p>Only fires when the attacker's main hand holds a Draconic Mace and the attacker currently owns
	 * the curse, so an ordinary mace or a mace outside the holder's inventory never triggers a rune.
	 */
	public static void onAttack(ServerPlayer attacker, Entity target) {
		if (attacker == null || target == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();

		if (server == null || !DragonMaceCurse.isActive(server, attacker.getUUID())) {
			return;
		}

		if (!DragonMaceProgression.isQualifyingWeapon(attacker.getMainHandItem())) {
			return;
		}

		if (!DragonMaceProgression.isEnabled(server, attacker.getUUID(), DragonMaceRuneCategory.ATTACK)) {
			return;
		}

		DragonMaceRune rune = DragonMaceProgression.runes(server, attacker.getUUID()).attack();
		level.sendParticles(attackParticle(rune), target.getX(), target.getY() + 1.0, target.getZ(),
				16, 0.3, 0.4, 0.3, 0.02);

		if (ignitesTarget(rune)) {
			target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
		}

		if (knocksBack(rune)) {
			Vec3 push = target.position().subtract(attacker.position());

			if (push.lengthSqr() > 1.0E-4) {
				Vec3 normalised = push.normalize().scale(0.45);
				target.push(normalised.x, 0.25, normalised.z);
				target.hurtMarked = true;
			}
		}

		if (target instanceof LivingEntity living) {
			attackEffect(rune).ifPresent(applied -> {
				if (applied.durationTicks() > 0) {
					living.addEffect(new MobEffectInstance(applied.effect(), applied.durationTicks(),
							applied.amplifier(), false, false, false));
				}
			});
		}
	}

	/**
	 * Trails the Aura rune around the current curse holder.
	 *
	 * <p>Purely cosmetic and recomputed from live state on every pass, so the aura stops the moment the
	 * curse is removed or the last Draconic Mace leaves the holder's inventory. Nothing is stored.
	 */
	public static void tickAura(MinecraftServer server) {
		if (server == null) {
			return;
		}

		UUID owner = DragonMaceCurse.activeOwner(server);

		if (owner == null) {
			return;
		}

		ServerPlayer player = server.getPlayerList().getPlayer(owner);

		if (player == null || !DragonMaceProgression.hasDraconicMace(player)) {
			return;
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		if (!DragonMaceProgression.isEnabled(server, owner, DragonMaceRuneCategory.AURA)) {
			return;
		}

		DragonMaceRune rune = DragonMaceProgression.runes(server, owner).aura();
		level.sendParticles(auraParticle(rune), player.getX(), player.getY() + 1.0, player.getZ(),
				6, 0.5, 0.8, 0.5, 0.01);
	}
}
