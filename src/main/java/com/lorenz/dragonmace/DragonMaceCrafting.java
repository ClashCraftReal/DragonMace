package com.lorenz.dragonmace;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The single authoritative gate for creating a Dragon Mace by crafting.
 *
 * <p>Every crafting path asks this class instead of inspecting item entities, so the persistent state
 * machine stays the only source of truth: a world that has never produced a mace may craft one, and
 * every other state (present, missing, deleted) refuses the recipe outright.
 */
public final class DragonMaceCrafting {
	private DragonMaceCrafting() {
	}

	/** Only a world that has never produced a mace may craft one. */
	public static boolean canCraft(DragonMaceOwnership state) {
		return state == DragonMaceOwnership.UNCRAFTED;
	}

	/** True when a computed crafting result must be hidden because the world already has a mace. */
	public static boolean suppressesResult(DragonMaceOwnership state, ItemStack result) {
		return DragonMaceIdentity.isDragonMace(result) && !canCraft(state);
	}

	/**
	 * Records a craft whose result has already been collected by the player.
	 *
	 * <p>Called from the result slot after the stack left the slot, so a refused or cancelled
	 * collection never reaches this method and never flips the world state.
	 *
	 * @return {@code true} when this call moved the world to {@link DragonMaceOwnership#PRESENT}
	 */
	public static boolean completeCraft(MinecraftServer server, Player player) {
		if (server == null || player == null) {
			return false;
		}

		DragonMaceWorldData data = DragonMaceWorldData.get(server);

		if (!data.markCrafted()) {
			return false;
		}

		data.setOwner(player.getUUID());
		DragonMaceWorldData.save(server);
		DragonMaceAnnouncements.crafted(server, player.getName().getString());
		DragonMace.LOGGER.info("Dragon Mace crafted by {}; world uniqueness flag set.", player.getName().getString());
		return true;
	}
}
