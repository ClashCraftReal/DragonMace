package com.lorenz.dragonmace;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceOfflinePlayerData;
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

	/** Outcome of an administrative removal, so every branch can be unit tested. */
	public enum RemoveResult {
		/** The target was the active holder and has been cleared. */
		REMOVED,
		/** The target is not the active holder, so nothing was changed. */
		NOT_OWNER,
		/** No target was supplied. */
		NO_TARGET
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

	/**
	 * Pure join decision: the curse is only reapplied to the player recorded as its holder.
	 *
	 * <p>Once {@code /dm remove} clears the recorded holder, this is {@code false} for everyone, so a
	 * removed player cannot regain the curse by reconnecting.
	 */
	public static boolean shouldApplyOnJoin(UUID owner, UUID player) {
		return owner != null && owner.equals(player);
	}

	public static boolean isActive(MinecraftServer server, UUID player) {
		return shouldApplyOnJoin(activeOwner(server), player);
	}

	/** Pure removal decision, kept separate so every branch can be unit tested. */
	public static RemoveResult decideRemoval(UUID currentOwner, UUID target) {
		if (target == null) {
			return RemoveResult.NO_TARGET;
		}

		if (currentOwner == null || !currentOwner.equals(target)) {
			return RemoveResult.NOT_OWNER;
		}

		return RemoveResult.REMOVED;
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
		// The holder's maces carry the configured enchantments, immediately.
		DragonMaceEnchantments.reconcileContainer(player.getInventory(), true, server.registryAccess(),
				DragonMaceConfig.get());
		DragonMaceAura.apply(player);
		DragonMaceTabList.apply(server, player);
		// Dragons Pride: the holder may carry at most two totems from the moment the curse lands.
		DragonMaceTotemLimit.enforce(player);
	}

	/** Removes every curse effect from a player who no longer holds the curse. */
	public static void clear(MinecraftServer server, ServerPlayer player) {
		DragonMaceAura.clear(player);
		DragonMaceTabList.clear(server, player);
		DragonMaceMaceConversion.revertInventory(player);
		// Nobody outside the holder may keep enchanted maces, so every mace here is stripped.
		DragonMaceEnchantments.reconcileContainer(player.getInventory(), false, server.registryAccess(),
				DragonMaceConfig.get());
	}

	/**
	 * Global removal: clears the curse for every player, online and offline.
	 *
	 * <p>The recorded holder is cleared and saved first, so no join, respawn, transfer or tick handler
	 * can reactivate the curse even if a later step fails. Every online player is then cleaned through
	 * the normal {@link #clear} path, which removes the aura, rebroadcasts the tablist tag and reverts
	 * the existing Draconic Mace. Finally every saved player file in {@code playerdata} is purged, so an
	 * offline holder does not keep the aura until they log in.
	 *
	 * <p>No Very Heavy Core is created and no item is deleted. {@link #withdraw} remains the holder's
	 * own voluntary trade and is untouched.
	 */
	public static GlobalRemoval removeAll(MinecraftServer server) {
		if (server == null) {
			return new GlobalRemoval(0, 0, 0, 0, 0);
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);
			data.setCurseOwner(null);
			DragonMaceWorldData.save(server);

			int online = 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				clear(server, player);
				online++;
			}

			DragonMaceOfflinePlayerData.DirectoryPurge purge = DragonMaceOfflinePlayerData
					.purgeDirectory(DragonMaceOfflinePlayerData.playerDataDirectory(server));

			GlobalRemoval result = new GlobalRemoval(online, purge.purged(), purge.alreadyClean(),
					purge.failed(), purge.skipped());

			DragonMace.LOGGER.info(
					"Global curse removal: {} online cleaned, {} files purged, {} already clean, {} failed, {} skipped.",
					result.online(), result.purged(), result.alreadyClean(), result.failed(), result.skipped());
			return result;
		}
	}

	/** Counts reported by {@link #removeAll}. */
	public record GlobalRemoval(int online, int purged, int alreadyClean, int failed, int skipped) {
		/** Every player that was cleaned, whether online or from saved data. */
		public int cleaned() {
			return this.online + this.purged + this.alreadyClean;
		}
	}

	/**
	 * Administrative removal, for an online or an offline holder.
	 *
	 * <p>The recorded holder is cleared first, so the curse can never reactivate on the next join even
	 * if the rest of the cleanup fails. An online holder then goes through the normal {@link #clear}
	 * path: aura, tablist tag and the existing Draconic Mace revert. An offline holder has the aura
	 * purged from their saved player data instead, because active effects live in that file; the file
	 * is rewritten atomically and never created, so a player who never joined is not given saved data.
	 *
	 * <p>No Very Heavy Core is created here. That is the difference between this administrative
	 * removal and {@link #withdraw}, which is the holder's own voluntary trade.
	 */
	public static boolean remove(MinecraftServer server, UUID target) {
		if (server == null || target == null) {
			return false;
		}

		synchronized (LOCK) {
			DragonMaceWorldData data = DragonMaceWorldData.get(server);

			if (decideRemoval(data.getCurseOwner(), target) != RemoveResult.REMOVED) {
				return false;
			}

			data.setCurseOwner(null);
			DragonMaceWorldData.save(server);

			ServerPlayer online = server.getPlayerList().getPlayer(target);

			if (online != null) {
				clear(server, online);
			} else {
				DragonMaceOfflinePlayerData.PurgeResult result =
						DragonMaceOfflinePlayerData.purgeCurseOffline(server, target);
				DragonMace.LOGGER.info("Offline curse removal for {}: {}.", target, result);
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
		DragonMaceMaceConversion.revertInventory(player);
		DragonMaceEnchantments.reconcileContainer(player.getInventory(), false, server.registryAccess(),
				DragonMaceConfig.get());
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
		DragonMaceMaceConversion.revertInventory(respawned);
		DragonMaceEnchantments.reconcileContainer(respawned.getInventory(), false,
				server.registryAccess(), DragonMaceConfig.get());
	}

	private static void giveCore(ServerPlayer player) {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();

		if (!player.getInventory().add(core)) {
			// A full inventory must never destroy the returned core.
			player.drop(core, false);
		}
	}
}
