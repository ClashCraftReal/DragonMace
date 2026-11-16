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

	/** Identifier of the Very Heavy Core crafting recipe. */
	public static final Identifier VERY_HEAVY_CORE_RECIPE =
			Identifier.fromNamespaceAndPath(DragonMace.MOD_ID, "very_heavy_core");

	/** Registry key of the Very Heavy Core recipe, used for recipe-book discovery. */
	public static final ResourceKey<Recipe<?>> VERY_HEAVY_CORE_KEY =
			ResourceKey.create(Registries.RECIPE, VERY_HEAVY_CORE_RECIPE);

	public static final String DRAGON_MACE_NAME = "Dragon Mace";

	private DragonMaceRecipes() {
	}

	public static boolean isVanillaMace(RecipeHolder<?> holder) {
		return holder.id().identifier().equals(VANILLA_MACE_RECIPE);
	}

	public static boolean isDragonMaceRecipe(RecipeHolder<?> holder) {
		return holder.id().identifier().equals(DRAGON_MACE_RECIPE);
	}

	public static boolean isVeryHeavyCoreRecipe(RecipeHolder<?> holder) {
		return holder.id().identifier().equals(VERY_HEAVY_CORE_RECIPE);
	}

	/**
	 * Drops the vanilla mace recipe and any pre-existing Dragon Mace or Very Heavy Core recipe, then
	 * adds both mod recipes. Every other recipe is preserved.
	 *
	 * <p>This runs on every recipe-map rebuild, which happens on server start and on every datapack
	 * reload, so both recipes survive restarts without any extra persistence.
	 */
	public static RecipeMap apply(RecipeMap recipes) {
		List<RecipeHolder<?>> kept = new ArrayList<>(recipes.values().size() + 2);

		for (RecipeHolder<?> holder : recipes.values()) {
			Identifier id = holder.id().identifier();

			if (id.equals(VANILLA_MACE_RECIPE) || id.equals(DRAGON_MACE_RECIPE)
					|| id.equals(VERY_HEAVY_CORE_RECIPE)) {
				continue;
			}

			kept.add(holder);
		}

		kept.add(createDragonMaceRecipe());
		kept.add(createVeryHeavyCoreRecipe());
		return RecipeMap.create(kept);
	}

	/**
	 * The Very Heavy Core recipe, in the exact 3x3 layout specified by the design.
	 *
	 * <pre>
	 * Conduit | Dragon Egg | Nether Star
	 * Creeper Head | Heavy Core | Piglin Head
	 * Wayfinder Template | Trident | Silence Template
	 * </pre>
	 *
	 * <p>All nine slots are real ingredients, so vanilla crafting consumes every one of them, including
	 * the Dragon Egg. The positions are fixed and must not be substituted or simplified.
	 *
	 * <p>The result is the dedicated {@code dragon_mace:very_heavy_core} item.
	 */
	public static RecipeHolder<ShapedRecipe> createVeryHeavyCoreRecipe() {
		ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of(
				'#', Ingredient.of(Items.CONDUIT),
				'o', Ingredient.of(Items.DRAGON_EGG),
				'N', Ingredient.of(Items.NETHER_STAR),
				'C', Ingredient.of(Items.CREEPER_HEAD),
				'H', Ingredient.of(Items.HEAVY_CORE),
				'P', Ingredient.of(Items.PIGLIN_HEAD),
				'W', Ingredient.of(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE),
				'T', Ingredient.of(Items.TRIDENT),
				'S', Ingredient.of(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)
		), "#oN", "CHP", "WTS");

		ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern,
				DragonMaceItems.createVeryHeavyCoreStack());
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, VERY_HEAVY_CORE_RECIPE);
		return new RecipeHolder<>(key, recipe);
	}

	public static RecipeHolder<ShapedRecipe> createDragonMaceRecipe() {
		ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of(
				'#', Ingredient.of(Items.CONDUIT),
				'H', Ingredient.of(Items.HEAVY_CORE),
				'N', Ingredient.of(Items.NETHER_STAR),
				'C', Ingredient.of(Items.CREEPER_HEAD),
				'o', Ingredient.of(Items.DRAGON_EGG),
				'P', Ingredient.of(Items.PIGLIN_HEAD),
				'W', Ingredient.of(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE),
				'T', Ingredient.of(Items.TRIDENT),
				'S', Ingredient.of(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)
		), "#HN", "CoP", "WTS");

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
