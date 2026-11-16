package com.lorenz.dragonmace;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Transfers the Dragon Mace to the player who kills its owner.
 *
 * <p>Runs at the head of {@code ServerPlayer.die}, before {@code dropAllDeathLoot}, so the mace is
 * taken out of the dying inventory exactly once. The same atomic move as {@code /transfermace} is
 * used when the killer has space. When the killer's inventory is full the original stack becomes a
 * real item entity at their feet and is reserved for them with {@code ItemEntity.setTarget}, which
 * is persisted by vanilla and blocks every other player from picking it up.
 */
public final class DragonMaceDeathTransfer {
	public enum Outcome {
		/** The victim is not the persisted owner, or the mace is not in a transferable state. */
		NOT_OWNER,
		/** The persisted owner has no marked mace in their inventory. */
		NO_MACE,
		/** The damage was not credited to a player; vanilla death drops handle the mace. */
		NO_KILLER,
		/** The owner damaged themselves. */
		SELF_KILL,
		/** The killer already holds the mace, so nothing is moved or dropped. */
		KILLER_HAS_MACE,
		/** The mace was inserted into the killer's inventory. */
		TRANSFERRED,
		/** The killer's inventory was full and the mace was dropped, reserved for them. */
		DROPPED_FOR_KILLER
	}

	/** Pure plan for the live transfer, kept separate so every branch can be unit tested. */
	public enum Plan {
		NO_MACE,
		KILLER_HAS_MACE,
		INSERT_INTO_KILLER,
		DROP_AT_KILLER
	}

	private DragonMaceDeathTransfer() {
	}

	public static Outcome transfer(MinecraftServer server, ServerPlayer victim, DamageSource source) {
		if (server == null || victim == null || source == null) {
			return Outcome.NO_KILLER;
		}

		DragonMaceWorldData data = DragonMaceWorldData.get(server);

		if (data.getState() != DragonMaceOwnership.PRESENT || !data.isOwner(victim.getUUID())) {
			return Outcome.NOT_OWNER;
		}

		ServerPlayer killer = resolveKiller(victim, source);

		if (killer == null) {
			return Outcome.NO_KILLER;
		}

		if (killer.getUUID().equals(victim.getUUID())) {
			return Outcome.SELF_KILL;
		}

		MaceInventory from = MaceInventory.of(victim.getInventory());
		MaceInventory to = MaceInventory.of(killer.getInventory());

		return switch (plan(from, to)) {
			case NO_MACE -> Outcome.NO_MACE;
			case KILLER_HAS_MACE -> Outcome.KILLER_HAS_MACE;
			case DROP_AT_KILLER -> dropAtKiller(server, data, victim, killer, from);
			case INSERT_INTO_KILLER -> insertIntoKiller(server, data, victim, killer, from, to);
		};
	}

	/** Decides the transfer destination without touching either inventory. */
	public static Plan plan(MaceInventory from, MaceInventory to) {
		if (DragonMaceTransfer.findDragonMaceSlot(from) < 0) {
			return Plan.NO_MACE;
		}

		if (DragonMaceTransfer.findDragonMaceSlot(to) >= 0) {
			return Plan.KILLER_HAS_MACE;
		}

		return to.freeSlot() < 0 ? Plan.DROP_AT_KILLER : Plan.INSERT_INTO_KILLER;
	}

	/**
	 * Writes the new owner and restores {@code PRESENT} if the saved state was stale.
	 *
	 * <p>Separated from the live transfer so ownership persistence can be tested without a server.
	 */
	public static boolean recordNewOwner(DragonMaceWorldData data, UUID killer) {
		if (data == null || killer == null) {
			return false;
		}

		data.setOwner(killer);

		if (data.getState() != DragonMaceOwnership.PRESENT) {
			data.markPresent();
		}

		return data.isOwner(killer);
	}

	private static Outcome insertIntoKiller(MinecraftServer server, DragonMaceWorldData data, ServerPlayer victim,
			ServerPlayer killer, MaceInventory from, MaceInventory to) {
		DragonMaceTransfer.Result moved = DragonMaceTransfer.moveMace(from, to);

		return switch (moved) {
			case SUCCESS -> {
				recordNewOwner(data, killer.getUUID());
				DragonMaceWorldData.save(server);
				announce(server, victim, killer);
				DragonMace.LOGGER.info("Dragon Mace kill transfer: {} -> {} (inventory)", victim.getName().getString(),
						killer.getName().getString());
				yield Outcome.TRANSFERRED;
			}
			case TARGET_HAS_MACE -> Outcome.KILLER_HAS_MACE;
			case NO_MACE -> Outcome.NO_MACE;
			// The capacity plan was checked first, but a concurrent change still must not lose the mace.
			case TARGET_FULL -> dropAtKiller(server, data, victim, killer, from);
			default -> Outcome.NO_MACE;
		};
	}

	private static Outcome dropAtKiller(MinecraftServer server, DragonMaceWorldData data, ServerPlayer victim,
			ServerPlayer killer, MaceInventory from) {
		int slot = DragonMaceTransfer.findDragonMaceSlot(from);

		if (slot < 0) {
			return Outcome.NO_MACE;
		}

		ItemStack original = from.get(slot);
		from.set(slot, ItemStack.EMPTY);

		ItemEntity entity = new ItemEntity(killer.level(), killer.getX(), killer.getY(), killer.getZ(), original);
		// The target UUID is saved by ItemEntity, so the reservation survives a restart and only the
		// intended killer can pick it up. setItem also applies the permanent ground protections.
		entity.setTarget(killer.getUUID());

		if (!killer.level().addFreshEntity(entity)) {
			// Put the original stack back before vanilla death drops run, so a failed insertion cannot
			// destroy the mace.
			from.set(slot, original);
			DragonMace.LOGGER.warn("Could not create the Dragon Mace drop for {}; the mace stays with {} for the "
					+ "normal death drop.", killer.getName().getString(), victim.getName().getString());
			return Outcome.NO_KILLER;
		}

		DragonMaceProtection.protectItemEntity(entity);
		recordNewOwner(data, killer.getUUID());
		DragonMaceWorldData.save(server);
		announce(server, victim, killer);
		DragonMace.LOGGER.info("Dragon Mace kill transfer: {} -> {} (dropped at killer's feet, reserved)",
				victim.getName().getString(), killer.getName().getString());
		return Outcome.DROPPED_FOR_KILLER;
	}

	/**
	 * Resolves the player credited with the kill.
	 *
	 * <p>Shared with {@link DragonMaceCurse#handleDeath} so the mace transfer and the curse transfer
	 * always agree on who the killer is. Covers direct melee, projectiles fired by a player and a
	 * player-owned mob, because {@code getKillCredit} carries the credited player for all of them.
	 */
	public static ServerPlayer resolveKiller(ServerPlayer victim, DamageSource source) {
		Entity causingEntity = source.getEntity();

		if (causingEntity instanceof ServerPlayer player) {
			return player;
		}

		// Covers a player-owned wolf or another credited kill where the causing entity is not the
		// player itself.
		LivingEntity credit = victim.getKillCredit();

		return credit instanceof ServerPlayer player ? player : null;
	}

	/**
	 * One announcement for both successful kill-transfer paths. The dropped path reserves the entity
	 * for the killer, so it uses exactly the same message as the direct-inventory path.
	 */
	private static void announce(MinecraftServer server, ServerPlayer victim, ServerPlayer killer) {
		DragonMaceAnnouncements.combatClaimed(server, killer.getName().getString(), victim.getName().getString());
	}
}
