package com.lorenz.dragonmace;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * The single authoritative owner of the Curse of the Dragons.
 *
 * <p>Exactly one player may hold the curse at a time. The holder is persisted in
 * {@link DragonMaceWorldData} by UUID, so the curse survives restarts, dimension changes and name
 * changes, and it is never derived from the mace owner or from any live entity.
 *
 * <p>Every live mutation runs on the server thread and is additionally guarded by {@link #LOCK}, so a
 * second activation can never race the first one into existence.
 */
public final class DragonMaceCurse {
	public enum ActivateResult {
		/** The curse was written to the player. */
		ACTIVATED,
		/** The same player already holds the curse. */
		ALREADY_ACTIVE,
		/** Another player holds the curse. */
		TAKEN,
		/** No player was supplied. */
		NO_PLAYER
	}

	private static final Object LOCK = new Object();

	private DragonMaceCurse() {
	}

	/** Pure activation decision, kept separate so every branch can be unit tested. */
	public static ActivateResult decideActivation(UUID currentOwner, UUID player) {
		if (player == null) {
			return ActivateResult.NO_PLAYER;
		}

		if (currentOwner == null) {
			return ActivateResult.ACTIVATED;
		}

		return currentOwner.equals(player) ? ActivateResult.ALREADY_ACTIVE : ActivateResult.TAKEN;
	}

	/** The persisted holder, or {@code null} when the curse is not active. */
	public static UUID activeOwner(MinecraftServer server) {
		return server == null ? null : DragonMaceWorldData.get(server).getCurseOwner();
	}

	public static boolean isActive(MinecraftServer server, UUID player) {
		UUID owner = activeOwner(server);
		return owner != null && owner.equals(player);
	}

	/**
	 * Writes the curse to {@code player} when nobody holds it.
	 *
	 * <p>The owner is persisted before any effect is applied, so a crash in the middle of the
	 * application cannot leave the curse unowned while the player already carries its effects.
	 */
	public static ActivateResult activate(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return ActivateResult.NO_PLAYER;
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);
			ActivateResult decision = decideActivation(data.getCurseOwner(), player.getUUID());

			if (decision != ActivateResult.ACTIVATED) {
				return decision;
			}

			data.setCurseOwner(player.getUUID());
			DragonMaceWorldData.save(server);
			apply(server, player);
			DragonMaceAnnouncements.coreConsumed(server, player.getName().getString());
			DragonMace.LOGGER.info("The Curse of the Dragons was activated by {}.", player.getName().getString());
			return ActivateResult.ACTIVATED;
		}
	}

	/** Applies every curse effect to a player who already holds the curse. */
	public static void apply(MinecraftServer server, ServerPlayer player) {
		DragonMaceMaceConversion.convertInventory(player, server.registryAccess());
		DragonMaceAura.apply(player);
		DragonMaceTabList.apply(server, player);
		DragonMaceTotemLimit.enforce(player);
	}

	/** Removes every curse effect from a player who no longer holds the curse. */
	public static void clear(MinecraftServer server, ServerPlayer player) {
		DragonMaceAura.clear(player);
		DragonMaceTabList.clear(server, player);
		DragonMaceMaceConversion.revertInventory(player);
	}

	/**
	 * Administrative removal.
	 *
	 * <p>An offline holder is cleared from the saved data only; the effects they still carry are
	 * cleaned up by {@link #onPlayerJoin} the next time they log in.
	 */
	public static boolean remove(MinecraftServer server, UUID target) {
		if (server == null || target == null) {
			return false;
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);

			if (!target.equals(data.getCurseOwner())) {
				return false;
			}

			data.setCurseOwner(null);
			DragonMaceWorldData.save(server);

			ServerPlayer online = server.getPlayerList().getPlayer(target);

			if (online != null) {
				clear(server, online);
			}

			DragonMace.LOGGER.info("The Curse of the Dragons was removed from {}.", target);
			return true;
		}
	}

	/**
	 * Withdraws the curse and hands one Very Heavy Core back.
	 *
	 * <p>The core is only created after the curse was actually cleared, so a refused withdrawal can
	 * never duplicate a core.
	 */
	public static boolean withdraw(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return false;
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);

			if (!player.getUUID().equals(data.getCurseOwner())) {
				return false;
			}

			data.setCurseOwner(null);
			DragonMaceWorldData.save(server);
			clear(server, player);
			giveCore(player);
			DragonMace.LOGGER.info("The Curse of the Dragons was withdrawn by {}.", player.getName().getString());
			return true;
		}
	}

	/** Moves the curse from a dead holder to their killer. */
	public static boolean transfer(MinecraftServer server, ServerPlayer victim, ServerPlayer killer) {
		if (server == null || victim == null || killer == null) {
			return false;
		}

		if (victim.getUUID().equals(killer.getUUID())) {
			return false;
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);

			if (!victim.getUUID().equals(data.getCurseOwner())) {
				return false;
			}

			data.setCurseOwner(killer.getUUID());
			DragonMaceWorldData.save(server);
			clear(server, victim);
			apply(server, killer);
			DragonMaceAnnouncements.curseTransferred(server, victim.getName().getString(),
					killer.getName().getString());
			DragonMace.LOGGER.info("The Curse of the Dragons transferred from {} to {}.",
					victim.getName().getString(), killer.getName().getString());
			return true;
		}
	}

	/**
	 * Death handling, called from the head of {@code ServerPlayer.die}.
	 *
	 * <p>A death without a credited player killer keeps the curse on the victim; it is reapplied when
	 * they respawn.
	 */
	public static boolean handleDeath(MinecraftServer server, ServerPlayer victim, DamageSource source) {
		if (server == null || victim == null || source == null) {
			return false;
		}

		if (!isActive(server, victim.getUUID())) {
			return false;
		}

		ServerPlayer killer = DragonMaceDeathTransfer.resolveKiller(victim, source);

		if (killer == null) {
			return false;
		}

		return transfer(server, victim, killer);
	}

	/** Restores the curse for a returning holder and cleans up a stale one for everyone else. */
	public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}

		if (isActive(server, player.getUUID())) {
			apply(server, player);
			return;
		}

		DragonMaceAura.clearStale(player);
		DragonMaceTabList.clear(server, player);
	}

	/** Restores the curse on the new player object after a respawn. */
	public static void onPlayerRespawn(MinecraftServer server, ServerPlayer respawned) {
		if (server == null || respawned == null) {
			return;
		}

		if (isActive(server, respawned.getUUID())) {
			apply(server, respawned);
			return;
		}

		DragonMaceAura.clearStale(respawned);
	}

	private static void giveCore(ServerPlayer player) {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();

		if (!player.getInventory().add(core)) {
			// A full inventory must never destroy the returned core.
			player.drop(core, false);
		}
	}
}
