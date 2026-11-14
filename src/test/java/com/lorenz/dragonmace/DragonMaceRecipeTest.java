package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Dragon Mace recipe through the real 1.21.11 recipe classes.
 */
class DragonMaceRecipeTest {
	private static ShapedRecipe recipe;

	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		recipe = DragonMaceRecipes.createDragonMaceRecipe().value();
	}

	private static CraftingInput grid(ItemStack topMiddle, ItemStack middleLeft, ItemStack middle,
			ItemStack middleRight, ItemStack bottomMiddle) {
		return CraftingInput.of(3, 3, List.of(
				ItemStack.EMPTY, topMiddle, ItemStack.EMPTY,
				middleLeft, middle, middleRight,
				ItemStack.EMPTY, bottomMiddle, ItemStack.EMPTY));
	}

	private static CraftingInput correctGrid() {
		return grid(new ItemStack(Items.HEAVY_CORE), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.DRAGON_EGG), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.BREEZE_ROD));
	}

	@Test
	void recipeHasTheRequestedShape() {
		assertEquals(3, recipe.getWidth());
		assertEquals(3, recipe.getHeight());
	}

	@Test
	void correctIngredientsMatch() {
		assertTrue(recipe.matches(correctGrid(), null));
	}

	@Test
	void wrongIngredientsDoNotMatch() {
		// Dragon egg replaced by a plain egg.
		assertFalse(recipe.matches(grid(new ItemStack(Items.HEAVY_CORE), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.EGG), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.BREEZE_ROD)), null));

		// Only one totem.
		assertFalse(recipe.matches(grid(new ItemStack(Items.HEAVY_CORE), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.DRAGON_EGG), ItemStack.EMPTY,
				new ItemStack(Items.BREEZE_ROD)), null));

		// Missing breeze rod.
		assertFalse(recipe.matches(grid(new ItemStack(Items.HEAVY_CORE), new ItemStack(Items.TOTEM_OF_UNDYING),
				new ItemStack(Items.DRAGON_EGG), new ItemStack(Items.TOTEM_OF_UNDYING),
				ItemStack.EMPTY), null));

		// The vanilla mace layout must not match either.
		assertFalse(recipe.matches(CraftingInput.of(3, 3, List.of(
				ItemStack.EMPTY, new ItemStack(Items.HEAVY_CORE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.BREEZE_ROD), ItemStack.EMPTY,
				ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)), null));
	}

	@Test
	void outputIsAMarkedVanillaMace() {
		ItemStack result = recipe.assemble(correctGrid(), null);

		assertTrue(result.is(Items.MACE), "output must be the vanilla mace item");
		assertEquals(1, result.getCount());
		assertTrue(DragonMaceIdentity.isDragonMace(result), "output must carry the Dragon Mace marker");
		assertEquals(DragonMaceRecipes.DRAGON_MACE_NAME, result.getHoverName().getString());
	}

	@Test
	void plainMaceIsNotADragonMace() {
		assertFalse(DragonMaceIdentity.isDragonMace(new ItemStack(Items.MACE)));
		assertFalse(DragonMaceIdentity.isDragonMace(ItemStack.EMPTY));
	}
}
