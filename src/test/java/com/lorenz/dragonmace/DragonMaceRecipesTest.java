package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceRecipesTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static RecipeHolder<?> holder(String id) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
		ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
				ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.STICK)), " # "),
				new ItemStack(Items.STICK));
		return new RecipeHolder<>(key, recipe);
	}

	private static Set<String> ids(RecipeMap map) {
		return map.values().stream()
				.map(h -> h.id().identifier().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void vanillaMaceRecipeIsRemovedAndOthersAreKept() {
		RecipeMap filtered = DragonMaceRecipes.apply(
				RecipeMap.create(List.of(holder("minecraft:mace"), holder("minecraft:stick"))));

		Set<String> ids = ids(filtered);

		assertFalse(ids.contains("minecraft:mace"), "vanilla mace recipe must be gone");
		assertTrue(ids.contains("minecraft:stick"), "unrelated recipes must survive");
		assertTrue(ids.contains("dragon-mace:dragon_mace"), "Dragon Mace recipe must be present");
	}

	@Test
	void applyingTwiceDoesNotDuplicateTheDragonMaceRecipe() {
		RecipeMap once = DragonMaceRecipes.apply(RecipeMap.create(List.of(holder("minecraft:mace"))));
		RecipeMap twice = DragonMaceRecipes.apply(once);

		assertEquals(once.values().size(), twice.values().size());
		assertEquals(1, twice.values().stream()
				.filter(h -> h.id().identifier().equals(DragonMaceRecipes.DRAGON_MACE_RECIPE))
				.count());
	}

	@Test
	void emptyMapGainsOnlyTheDragonMaceRecipe() {
		RecipeMap filtered = DragonMaceRecipes.apply(RecipeMap.EMPTY);

		assertEquals(Set.of("dragon-mace:dragon_mace"), ids(filtered));
	}

	@Test
	void dragonMaceRecipeIsAShapedCraftingRecipe() {
		RecipeHolder<ShapedRecipe> holder = DragonMaceRecipes.createDragonMaceRecipe();

		assertEquals(DragonMaceRecipes.DRAGON_MACE_RECIPE, holder.id().identifier());
		assertNotNull(holder.value());
		assertEquals(net.minecraft.world.item.crafting.RecipeType.CRAFTING, holder.value().getType());
	}
}
