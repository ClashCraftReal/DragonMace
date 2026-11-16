package com.lorenz.dragonmace;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Atomic, server-side transfer of the unique Dragon Mace between players.
 *
 * <p>Shared by {@code /transfermace} and the planned kill-transfer mechanic so both go through the
 * same validated path. The transfer never creates an item entity: the stack is moved directly
 * between inventories, and the persistent owner is updated in the same call.
 */
public final class DragonMaceTransfer {
	public enum Result {
		SUCCESS,
		NOT_OWNER,
		TARGET_OFFLINE,
		SELF_TRANSFER,
		TARGET_HAS_MACE,
		NO_MACE,
		TARGET_FULL
	}

	private DragonMaceTransfer() {
	}

	public static Result transfer(MinecraftServer server, ServerPlayer from, ServerPlayer to) {
		if (from == null) {
			return Result.NOT_OWNER;
		}

		if (to == null) {
			return Result.TARGET_OFFLINE;
		}

		DragonMaceWorldData data = DragonMaceWorldData.get(server);
		Result validation = validate(from.getUUID(), to.getUUID(), data.getOwner(),
				DragonMaceRespawn.inventoryContainsDragonMace(to.getInventory()));

		if (validation != Result.SUCCESS) {
			return validation;
		}

		Result result = moveMace(MaceInventory.of(from.getInventory()), MaceInventory.of(to.getInventory()));

		if (result == Result.SUCCESS) {
			// Owner and state are written only after the stack actually moved, so a rejected transfer
			// can never leave the persistent owner pointing at a player who does not hold the mace.
			data.setOwner(to.getUUID());
			data.markPresent();
			DragonMaceWorldData.save(server);

			DragonMace.LOGGER.info("Dragon Mace transferred from {} to {}", from.getName().getString(),
					to.getName().getString());
		}

		return result;
	}

	/**
	 * Pure validation of a transfer request, kept separate from the live player objects so every
	 * rejection branch can be unit tested.
	 *
	 * @param fromId        the requesting player, or {@code null} when the source is not a player
	 * @param toId          the target player, or {@code null} when the target is offline
	 * @param owner         the persisted owner, or {@code null} when no owner is recorded
	 * @param targetHasMace whether the target already holds a marked mace
	 * @return {@link Result#SUCCESS} when the transfer may proceed, otherwise the rejection reason
	 */
	public static Result validate(UUID fromId, UUID toId, UUID owner, boolean targetHasMace) {
		if (toId == null) {
			return Result.TARGET_OFFLINE;
		}

		if (fromId == null) {
			return Result.NOT_OWNER;
		}

		if (fromId.equals(toId)) {
			return Result.SELF_TRANSFER;
		}

		if (owner == null || !owner.equals(fromId)) {
			return Result.NOT_OWNER;
		}

		if (targetHasMace) {
			return Result.TARGET_HAS_MACE;
		}

		return Result.SUCCESS;
	}

	/**
	 * Moves exactly one marked stack between two inventories.
	 *
	 * <p>Capacity is checked before anything is removed, and a partial insert is rolled back by
	 * removing what was added and restoring the original stack, so the mace can neither be duplicated
	 * nor destroyed by a failed transfer.
	 */
	public static Result moveMace(MaceInventory from, MaceInventory to) {
		int slot = findDragonMaceSlot(from);

		if (slot < 0) {
			return Result.NO_MACE;
		}

		if (findDragonMaceSlot(to) >= 0) {
			return Result.TARGET_HAS_MACE;
		}

		if (to.freeSlot() < 0) {
			return Result.TARGET_FULL;
		}

		ItemStack original = from.get(slot).copy();
		ItemStack moving = original.copy();
		from.set(slot, ItemStack.EMPTY);

		if (to.add(moving) && moving.isEmpty()) {
			return Result.SUCCESS;
		}

		// Roll back: take back whatever actually landed in the target, then restore the original.
		int added = original.getCount() - moving.getCount();

		if (added > 0) {
			removeMarked(to, added);
		}

		from.set(slot, original);
		return Result.TARGET_FULL;
	}

	public static int findDragonMaceSlot(MaceInventory inventory) {
		for (int slot = 0; slot < inventory.size(); slot++) {
			if (DragonMaceIdentity.isDragonMace(inventory.get(slot))) {
				return slot;
			}
		}

		return -1;
	}

	/** Removes up to {@code count} marked items, used only to undo a partial insert. */
	private static void removeMarked(MaceInventory inventory, int count) {
		int remaining = count;

		for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
			ItemStack stack = inventory.get(slot);

			if (!DragonMaceIdentity.isDragonMace(stack)) {
				continue;
			}

			int taken = Math.min(remaining, stack.getCount());
			remaining -= taken;

			if (taken >= stack.getCount()) {
				inventory.set(slot, ItemStack.EMPTY);
			} else {
				ItemStack reduced = stack.copy();
				reduced.setCount(stack.getCount() - taken);
				inventory.set(slot, reduced);
			}
		}
	}
}
