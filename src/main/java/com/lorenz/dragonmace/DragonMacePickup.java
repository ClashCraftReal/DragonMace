package com.lorenz.dragonmace;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent ownership changes caused by a successful pickup.
 *
 * <p>The item entity's {@code target} UUID is only a temporary pickup reservation used by the
 * kill-transfer drop. The persistent owner lives in {@link DragonMaceWorldData}; the two values are
 * deliberately handled separately so clearing a reservation cannot erase the owner.
 */
public final class DragonMacePickup {
	public enum Outcome {
		/** No persistent change, or the same player picked the mace up again. */
		NONE,
		/** The mace had no owner yet: the first claim. */
		CLAIMED,
		/** A different player now owns the mace. */
		CHANGES_HANDS
	}

	private DragonMacePickup() {
	}

	/**
	 * The vanilla item entity's reservation check, extracted so the reserved-drop rules can be tested.
	 *
	 * @param reservation the temporary target UUID, or {@code null} when the mace is free
	 * @param player      the player attempting the pickup
	 * @return {@code true} when this player may pick the mace up
	 */
	public static boolean canPickUp(UUID reservation, UUID player) {
		return reservation == null || reservation.equals(player);
	}

	/**
	 * Records a pickup that already succeeded.
	 *
	 * <p>Ownership is written only after the inventory accepted the stack; a failed touch never
	 * reaches this method. The state is self-healed to PRESENT if an older save was stale.
	 *
	 * @return the announcement that should be sent, or {@link Outcome#NONE} when nothing changed
	 */
	public static Outcome recordPickup(DragonMaceWorldData data, UUID player) {
		if (data == null || player == null) {
			return Outcome.NONE;
		}

		UUID previousOwner = data.getOwner();
		data.setOwner(player);

		if (data.getState() != DragonMaceOwnership.PRESENT) {
			data.markPresent();
		}

		if (previousOwner == null) {
			return Outcome.CLAIMED;
		}

		return previousOwner.equals(player) ? Outcome.NONE : Outcome.CHANGES_HANDS;
	}

	/**
	 * Clears the item entity's temporary pickup reservation.
	 *
	 * <p>Called after the pickup was accepted. The entity is normally discarded at this point, but
	 * clearing the field keeps the reservation and persistent ownership independent in every path.
	 */
	public static void clearReservation(ItemEntity entity) {
		if (entity != null) {
			entity.setTarget(null);
		}
	}

	/**
	 * Live pickup completion, called at the tail of {@code ItemEntity.playerTouch}.
	 *
	 * <p>A successful pickup is the only path where the entity was discarded by {@code playerTouch}
	 * and still holds the marked stack (vanilla restores the count after discarding). A failed pickup
	 * leaves the entity alive and therefore never changes ownership or announces anything.
	 */
	public static Outcome completePickup(MinecraftServer server, Player player, ItemEntity entity) {
		if (server == null || player == null || entity == null) {
			return Outcome.NONE;
		}

		if (!entity.isRemoved() || !DragonMaceIdentity.isDragonMace(entity.getItem())) {
			return Outcome.NONE;
		}

		DragonMaceWorldData data = DragonMaceWorldData.get(server);
		Outcome outcome = recordPickup(data, player.getUUID());
		clearReservation(entity);
		DragonMaceWorldData.save(server);

		switch (outcome) {
			case CLAIMED -> DragonMaceAnnouncements.claimed(server, player.getName().getString());
			case CHANGES_HANDS -> DragonMaceAnnouncements.changedHands(server, player.getName().getString());
			case NONE -> {
			}
		}

		DragonMace.LOGGER.info("Dragon Mace picked up by {} ({}).", player.getName().getString(), outcome);
		return outcome;
	}
}
