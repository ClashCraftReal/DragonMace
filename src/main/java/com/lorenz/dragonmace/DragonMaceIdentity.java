package com.lorenz.dragonmace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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

	/** Key inside {@code minecraft:custom_data} that marks a mace as the Draconic Mace. */
	public static final String DRACONIC_KEY = "draconic_mace";

	/** Key recording that the Draconic Mace placeholder enchantments were added by this mod. */
	public static final String PLACEHOLDER_ENCHANTMENTS_KEY = "draconic_placeholder_enchantments";

	/** Key holding the placeholder Draconic Rune level, read by the future enchantment system. */
	public static final String DRACONIC_RUNE_KEY = "draconic_rune";

	/** Key inside {@code minecraft:custom_data} that marks a paper stack as the Very Heavy Core. */
	public static final String VERY_HEAVY_CORE_KEY = "very_heavy_core";

	/** Key holding the custom identifier string {@code dragon_mace:very_heavy_core}. */
	public static final String VERY_HEAVY_CORE_ID_KEY = "very_heavy_core_id";

	private DragonMaceIdentity() {
	}

	public static boolean isDragonMace(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.MACE)) {
			return false;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
	}

	/** True when the stack is a mace carrying the Draconic Mace marker. */
	public static boolean isDraconicMace(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.MACE)) {
			return false;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(DRACONIC_KEY, false);
	}

	/**
	 * True when the stack is a {@code minecraft:paper} carrying the Very Heavy Core marker.
	 *
	 * <p>Both the item type and the custom identifier are required, so ordinary paper can never be
	 * identified as a Very Heavy Core and a paper stack cannot be promoted by its display name.
	 */
	public static boolean isVeryHeavyCore(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.PAPER)) {
			return false;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);

		if (data == null) {
			return false;
		}

		CompoundTag tag = data.copyTag();
		return tag.getBooleanOr(VERY_HEAVY_CORE_KEY, false)
				&& DragonMaceItems.VERY_HEAVY_CORE_ID.toString()
						.equals(tag.getStringOr(VERY_HEAVY_CORE_ID_KEY, ""));
	}

	/**
	 * True when the stack is a dragon egg item.
	 *
	 * <p>The egg is identified by its vanilla item, not by a name, so a renamed or re-lored egg is
	 * still recognised. Vanilla never produces a second dragon egg item type, so no marker is needed.
	 */
	public static boolean isDragonEgg(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.DRAGON_EGG);
	}

	/**
	 * Returns a mutable copy of the stack's custom data, or an empty tag when it has none.
	 *
	 * <p>{@code CustomData.copyTag()} already returns a detached copy, so callers may write to it and
	 * store it back without touching the original component.
	 */
	public static CompoundTag copyTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}
}
