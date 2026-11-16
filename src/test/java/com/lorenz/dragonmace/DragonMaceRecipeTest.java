package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
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
		TestBootstrap.boot();
		recipe = DragonMaceRecipes.createDragonMaceRecipe().value();
	}

	private static CraftingInput grid(ItemStack... slots) {
		return CraftingInput.of(3, 3, List.of(slots));
	}

	private static CraftingInput gridOf(Item... items) {
		ItemStack[] slots = new ItemStack[9];

		for (int i = 0; i < 9; i++) {
			slots[i] = items[i] == null ? ItemStack.EMPTY : new ItemStack(items[i]);
		}

		return grid(slots);
	}

	private static Item[] correctItems() {
		return new Item[] {
				Items.CONDUIT, Items.HEAVY_CORE, Items.NETHER_STAR,
				Items.CREEPER_HEAD, Items.DRAGON_EGG, Items.PIGLIN_HEAD,
				Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TRIDENT,
				Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE
		};
	}

	private static CraftingInput correctGrid() {
		return gridOf(correctItems());
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
	void everySlotIsRequired() {
		for (int slot = 0; slot < 9; slot++) {
			Item[] items = correctItems();
			items[slot] = null;

			assertFalse(recipe.matches(gridOf(items), null),
					"recipe must not match with slot " + slot + " empty");
		}
	}

	@Test
	void wrongIngredientsDoNotMatch() {
		// Conduit replaced by a plain prismarine shard.
		Item[] noConduit = correctItems();
		noConduit[0] = Items.PRISMARINE_SHARD;
		assertFalse(recipe.matches(gridOf(noConduit), null));

		// Dragon egg replaced by a plain egg.
		Item[] noDragonEgg = correctItems();
		noDragonEgg[4] = Items.EGG;
		assertFalse(recipe.matches(gridOf(noDragonEgg), null));

		// Nether star replaced by a totem of undying.
		Item[] noStar = correctItems();
		noStar[2] = Items.TOTEM_OF_UNDYING;
		assertFalse(recipe.matches(gridOf(noStar), null));

		// Trident replaced by a breeze rod.
		Item[] noTrident = correctItems();
		noTrident[7] = Items.BREEZE_ROD;
		assertFalse(recipe.matches(gridOf(noTrident), null));

		// Wayfinder template replaced by a silence template.
		Item[] swappedTemplate = correctItems();
		swappedTemplate[6] = Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
		assertFalse(recipe.matches(gridOf(swappedTemplate), null));
	}

	@Test
	void ingredientsAreNotInterchangeableBetweenSlots() {
		Item[] swapped = correctItems();
		Item first = swapped[0];
		swapped[0] = swapped[2];
		swapped[2] = first;

		assertFalse(recipe.matches(gridOf(swapped), null), "conduit and nether star must not swap");
	}

	@Test
	void previousStageOneLayoutNoLongerMatches() {
		assertFalse(recipe.matches(gridOf(
				null, Items.HEAVY_CORE, null,
				Items.TOTEM_OF_UNDYING, Items.DRAGON_EGG, Items.TOTEM_OF_UNDYING,
				null, Items.BREEZE_ROD, null), null));
	}

	@Test
	void vanillaMaceLayoutDoesNotMatch() {
		assertFalse(recipe.matches(gridOf(
				null, Items.HEAVY_CORE, null,
				null, Items.BREEZE_ROD, null,
				null, null, null), null));
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
