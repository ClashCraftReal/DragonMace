package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Builds the Dragon Mace recipe and rewrites the server recipe map.
 *
 * <p>The recipe is registered in code rather than shipped as a datapack file so the mod needs no
 * resource-loading dependency and the recipe cannot be removed or replaced by a datapack.
 */
public final class DragonMaceRecipes {
	/** Identifier of the vanilla mace crafting recipe. */
	public static final Identifier VANILLA_MACE_RECIPE = Identifier.withDefaultNamespace("mace");

	/** Identifier of the Dragon Mace crafting recipe. */
	public static final Identifier DRAGON_MACE_RECIPE =
			Identifier.fromNamespaceAndPath(DragonMace.MOD_ID, "dragon_mace");

	public static final String DRAGON_MACE_NAME = "Dragon Mace";

	private DragonMaceRecipes() {
	}

	public static boolean isVanillaMace(RecipeHolder<?> holder) {
		return holder.id().identifier().equals(VANILLA_MACE_RECIPE);
	}

	/**
	 * Drops the vanilla mace recipe and any pre-existing Dragon Mace recipe, then adds the Dragon
	 * Mace recipe. Every other recipe is preserved.
	 */
	public static RecipeMap apply(RecipeMap recipes) {
		List<RecipeHolder<?>> kept = new ArrayList<>(recipes.values().size() + 1);

		for (RecipeHolder<?> holder : recipes.values()) {
			Identifier id = holder.id().identifier();

			if (id.equals(VANILLA_MACE_RECIPE) || id.equals(DRAGON_MACE_RECIPE)) {
				continue;
			}

			kept.add(holder);
		}

		kept.add(createDragonMaceRecipe());
		return RecipeMap.create(kept);
	}

	public static RecipeHolder<ShapedRecipe> createDragonMaceRecipe() {
		ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of(
				'#', Ingredient.of(Items.HEAVY_CORE),
				'T', Ingredient.of(Items.TOTEM_OF_UNDYING),
				'D', Ingredient.of(Items.DRAGON_EGG),
				'B', Ingredient.of(Items.BREEZE_ROD)
		), " # ", "TDT", " B ");

		ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, pattern, createDragonMaceStack());
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, DRAGON_MACE_RECIPE);
		return new RecipeHolder<>(key, recipe);
	}

	public static ItemStack createDragonMaceStack() {
		ItemStack stack = new ItemStack(Items.MACE);

		CompoundTag marker = new CompoundTag();
		marker.putBoolean(DragonMaceIdentity.MARKER_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

		stack.set(DataComponents.CUSTOM_NAME, Component.literal(DRAGON_MACE_NAME)
				.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)));

		return stack;
	}
}
