package com.lorenz.dragonmace;

import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Recipe-book discovery for the Very Heavy Core.
 *
 * <p>The recipe is unlocked the moment a player obtains a Dragon Egg, which is the ingredient that
 * gates the craft. Unlocking goes through {@code ServerPlayer.awardRecipesByKey}, the vanilla path
 * that adds the recipe to {@code ServerRecipeBook}, pushes the recipe display to the client and fires
 * {@code RECIPE_UNLOCKED}. The known set is persisted in the player's saved data, so the unlock is
 * permanent for that player and survives reconnects and restarts.
 *
 * <p>This is done in code rather than through a datapack advancement because the mod's {@code data/}
 * resources are not loaded as a datapack in this environment; see {@code PROJECT_STATUS.md}.
 */
public final class DragonMaceRecipeUnlock {
	private DragonMaceRecipeUnlock() {
	}

	/** True when the player carries a Dragon Egg anywhere in their inventory. */
	public static boolean hasDragonEgg(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (DragonMaceIdentity.isDragonEgg(inventory.getItem(slot))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Unlocks the Very Heavy Core recipe for one player.
	 *
	 * @return {@code true} when the recipe was newly unlocked by this call
	 */
	public static boolean unlockVeryHeavyCore(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		ServerRecipeBook book = player.getRecipeBook();
		ResourceKey<Recipe<?>> key = DragonMaceRecipes.VERY_HEAVY_CORE_KEY;

		if (book.contains(key)) {
			return false;
		}

		player.awardRecipesByKey(List.of(key));
		DragonMace.LOGGER.info("Unlocked the Very Heavy Core recipe for {}.",
				player.getName().getString());
		return true;
	}

	/**
	 * Unlocks the recipe when the player holds a Dragon Egg.
	 *
	 * <p>Used on join, so a player who already owned an egg before this hotfix still gets the recipe.
	 *
	 * @return {@code true} when the recipe was newly unlocked by this call
	 */
	public static boolean unlockIfHoldingEgg(ServerPlayer player) {
		if (!hasDragonEgg(player)) {
			return false;
		}

		return unlockVeryHeavyCore(player);
	}
}
