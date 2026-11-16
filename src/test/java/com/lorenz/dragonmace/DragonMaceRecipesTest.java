package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceRecipesTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static Set<String> ids(RecipeMap map) {
		return map.values().stream()
				.map(holder -> holder.id().identifier().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void emptyMapGainsBothModRecipes() {
		RecipeMap filtered = DragonMaceRecipes.apply(RecipeMap.EMPTY);

		assertEquals(Set.of("dragon-mace:dragon_mace", "dragon-mace:very_heavy_core"), ids(filtered));
	}

	@Test
	void veryHeavyCoreRecipeIsPresentAndShaped() {
		RecipeMap filtered = DragonMaceRecipes.apply(RecipeMap.EMPTY);

		assertTrue(ids(filtered).contains("dragon-mace:very_heavy_core"),
				"Very Heavy Core recipe must be present");

		RecipeHolder<ShapedRecipe> holder = DragonMaceRecipes.createVeryHeavyCoreRecipe();

		assertEquals(DragonMaceRecipes.VERY_HEAVY_CORE_RECIPE, holder.id().identifier());
		assertEquals(RecipeType.CRAFTING, holder.value().getType());
	}

	@Test
	void veryHeavyCoreRecipeResultIsThePaperCore() {
		RecipeHolder<ShapedRecipe> holder = DragonMaceRecipes.createVeryHeavyCoreRecipe();
		ItemStack result = holder.value().assemble(null, null);

		assertTrue(result.is(Items.PAPER), "the result material must be minecraft:paper");
		assertTrue(DragonMaceIdentity.isVeryHeavyCore(result), "the result must carry the identifier");
		assertEquals("dragon_mace:very_heavy_core", DragonMaceItems.VERY_HEAVY_CORE_ID.toString());
	}

	@Test
	void applyingTwiceDoesNotDuplicateTheVeryHeavyCoreRecipe() {
		RecipeMap once = DragonMaceRecipes.apply(RecipeMap.EMPTY);
		RecipeMap twice = DragonMaceRecipes.apply(once);

		assertEquals(once.values().size(), twice.values().size());
		assertEquals(1, twice.values().stream()
				.filter(DragonMaceRecipes::isVeryHeavyCoreRecipe)
				.count());
	}

	/** The exact nine ingredients, in row-major order. */
	private static final List<Item> EXPECTED = List.of(
			Items.CONDUIT, Items.DRAGON_EGG, Items.NETHER_STAR,
			Items.CREEPER_HEAD, Items.HEAVY_CORE, Items.PIGLIN_HEAD,
			Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TRIDENT,
			Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);

	@Test
	void everyIngredientSitsInItsExactSpecifiedPosition() {
		List<Optional<Ingredient>> ingredients =
				DragonMaceRecipes.createVeryHeavyCoreRecipe().value().getIngredients();

		assertEquals(9, ingredients.size(), "the recipe must be a full 3x3 grid");

		for (int slot = 0; slot < EXPECTED.size(); slot++) {
			Item expected = EXPECTED.get(slot);
			Optional<Ingredient> ingredient = ingredients.get(slot);

			assertTrue(ingredient.isPresent(), "slot " + slot + " must hold an ingredient");
			assertTrue(ingredient.get().test(new ItemStack(expected)),
					"slot " + slot + " must accept " + expected);
		}
	}

	@Test
	void noSlotAcceptsNetheriteIngots() {
		List<Optional<Ingredient>> ingredients =
				DragonMaceRecipes.createVeryHeavyCoreRecipe().value().getIngredients();

		for (int slot = 0; slot < ingredients.size(); slot++) {
			assertFalse(ingredients.get(slot).orElseThrow().test(new ItemStack(Items.NETHERITE_INGOT)),
					"slot " + slot + " must not accept a Netherite Ingot");
		}
	}

	@Test
	void everySlotIsFilledSoCraftingConsumesAllNineIngredients() {
		List<Optional<Ingredient>> ingredients =
				DragonMaceRecipes.createVeryHeavyCoreRecipe().value().getIngredients();

		assertEquals(9, ingredients.stream().filter(Optional::isPresent).count(),
				"every slot must be a real ingredient, so vanilla crafting consumes all nine");
	}

	private static CraftingInput grid(List<Item> items) {
		List<ItemStack> stacks = new ArrayList<>(9);

		for (Item item : items) {
			stacks.add(new ItemStack(item));
		}

		return CraftingInput.of(3, 3, stacks);
	}

	@Test
	void recipeMatchesTheSpecifiedLayout() {
		ShapedRecipe recipe = DragonMaceRecipes.createVeryHeavyCoreRecipe().value();

		assertTrue(recipe.matches(grid(EXPECTED), null),
				"the recipe must match the specified nine-ingredient layout");
	}

	@Test
	void recipeDoesNotMatchWithoutTheDragonEgg() {
		ShapedRecipe recipe = DragonMaceRecipes.createVeryHeavyCoreRecipe().value();
		List<Item> swapped = new ArrayList<>(EXPECTED);
		swapped.set(1, Items.NETHERITE_INGOT);

		assertFalse(recipe.matches(grid(swapped), null),
				"the recipe must not match when the Dragon Egg is replaced");
	}

	@Test
	void recipeDoesNotMatchWhenTheLayoutIsShifted() {
		ShapedRecipe recipe = DragonMaceRecipes.createVeryHeavyCoreRecipe().value();
		List<Item> shifted = new ArrayList<>(EXPECTED);
		shifted.set(0, Items.DRAGON_EGG);
		shifted.set(1, Items.CONDUIT);

		assertFalse(recipe.matches(grid(shifted), null),
				"the recipe must not match when two ingredients swap positions");
	}

	@Test
	void craftingYieldsThePaperVeryHeavyCore() {
		ShapedRecipe recipe = DragonMaceRecipes.createVeryHeavyCoreRecipe().value();
		ItemStack result = recipe.assemble(grid(EXPECTED), null);

		assertTrue(result.is(Items.PAPER));
		assertTrue(DragonMaceIdentity.isVeryHeavyCore(result));
	}

	@Test
	void dragonMaceRecipeStillUsesItsOwnDragonEggSlot() {
		List<Optional<Ingredient>> ingredients =
				DragonMaceRecipes.createDragonMaceRecipe().value().getIngredients();

		assertTrue(ingredients.stream()
						.filter(Optional::isPresent)
						.map(Optional::get)
						.anyMatch(ingredient -> ingredient.test(new ItemStack(Items.DRAGON_EGG))),
				"the Dragon Mace recipe must keep its Dragon Egg ingredient");
	}
}
