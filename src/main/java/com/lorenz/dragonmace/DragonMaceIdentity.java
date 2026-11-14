package com.lorenz.dragonmace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Identity marker for the Dragon Mace.
 *
 * <p>The Dragon Mace is a plain vanilla {@code minecraft:mace} carrying a persistent
 * {@code minecraft:custom_data} entry. Custom data is stored in the item's data components, so it
 * survives dropping, picking up, saving and loading, and it is readable by the server without any
 * client-side registry entry. Ordinary crafting always produces a fresh stack from a recipe, so the
 * marker can never be copied onto another mace by crafting.
 */
public final class DragonMaceIdentity {
	/** Key inside {@code minecraft:custom_data} that marks a mace as the Dragon Mace. */
	public static final String MARKER_KEY = "dragon_mace";

	private DragonMaceIdentity() {
	}

	public static boolean isDragonMace(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.MACE)) {
			return false;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
	}
}
