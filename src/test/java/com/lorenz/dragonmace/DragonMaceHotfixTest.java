package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the anvil, Dragons Pride and vanilla mace crafting hotfixes.
 */
class DragonMaceHotfixTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack totem() {
		return new ItemStack(Items.TOTEM_OF_UNDYING);
	}

	private static ItemStack draconic() {
		return DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), null);
	}

	// --- Anvil -------------------------------------------------------------------------------

	@Test
	void aChestStillRevertsAMaceThatLeaves() {
		assertFalse(DragonMaceProtection.isAllowedContainer(new SimpleContainer(27)),
				"a chest must still revert a mace that leaves the inventory");
	}

	@Test
	void revertLeavingStillStripsAMaceThatReallyLeaves() {
		ItemStack reverted = DragonMaceMaceConversion.revertLeaving(draconic());

		assertFalse(DragonMaceIdentity.isDraconicMace(reverted),
				"the revert itself must still work");
	}

	// --- Dragons Pride -----------------------------------------------------------------------

	@Test
	void twoTotemsAreAllowed() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(1, totem()),
				"one carried plus one incoming is exactly the limit");
	}

	@Test
	void aThirdTotemIsRefused() {
		assertFalse(DragonMaceTotemLimit.allowsAdd(2, totem()));
	}

	@Test
	void aStackThatWouldOvershootIsRefused() {
		ItemStack two = totem();
		two.setCount(2);

		assertFalse(DragonMaceTotemLimit.allowsAdd(1, two),
				"one carried plus two incoming exceeds the limit of two");
	}

	@Test
	void anEmptyInventoryAcceptsATotem() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(0, totem()));
	}

	@Test
	void aNonTotemIsNeverRefused() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(2, new ItemStack(Items.MACE)));
	}

	@Test
	void theLimitIsTwo() {
		assertEquals(2, DragonMaceTotemLimit.MAX_TOTEMS);
	}

	@Test
	void excessIsZeroUpToTheLimit() {
		assertEquals(0, DragonMaceTotemLimit.excess(0));
		assertEquals(0, DragonMaceTotemLimit.excess(2));
	}

	@Test
	void excessCountsEverythingAboveTheLimit() {
		assertEquals(1, DragonMaceTotemLimit.excess(3));
	}

	// --- Vanilla mace crafting ---------------------------------------------------------------

	private static Set<String> ids(RecipeMap map) {
		return map.values().stream()
				.map(holder -> holder.id().identifier().toString())
				.collect(Collectors.toSet());
	}

	private static RecipeHolder<ShapedRecipe> vanillaMaceHolder() {
		ShapedRecipe recipe = DragonMaceRecipes.createVeryHeavyCoreRecipe().value();
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
				DragonMaceRecipes.VANILLA_MACE_RECIPE);
		return new RecipeHolder<>(key, recipe);
	}

	@Test
	void theVanillaMaceRecipeSurvivesTheRecipeRewrite() {
		RecipeMap withVanilla = RecipeMap.create(List.of(vanillaMaceHolder()));

		Set<String> kept = ids(DragonMaceRecipes.apply(withVanilla));

		assertTrue(kept.contains("minecraft:mace"),
				"the vanilla mace recipe must no longer be dropped");
	}

	@Test
	void theModRecipesAreStillAddedAlongsideTheVanillaMaceRecipe() {
		RecipeMap withVanilla = RecipeMap.create(List.of(vanillaMaceHolder()));

		Set<String> kept = ids(DragonMaceRecipes.apply(withVanilla));

		assertTrue(kept.contains("dragon-mace:dragon_mace"));
		assertTrue(kept.contains("dragon-mace:very_heavy_core"));
	}

	@Test
	void applyingTwiceDoesNotDuplicateTheVanillaMaceRecipe() {
		RecipeMap once = DragonMaceRecipes.apply(RecipeMap.create(List.of(vanillaMaceHolder())));
		RecipeMap twice = DragonMaceRecipes.apply(once);

		assertEquals(once.values().size(), twice.values().size());
	}

	@Test
	void theVanillaMaceRecipeIdentifierIsUnchanged() {
		assertEquals("minecraft:mace", DragonMaceRecipes.VANILLA_MACE_RECIPE.toString());
	}

	@Test
	void theVeryHeavyCoreRecipeIsStillPresent() {
		Set<String> kept = ids(DragonMaceRecipes.apply(RecipeMap.EMPTY));

		assertTrue(kept.contains("dragon-mace:very_heavy_core"));
	}

	@Test
	void aRevertedMaceIsStillNotTheSameStackObject() {
		ItemStack source = draconic();
		ItemStack reverted = DragonMaceMaceConversion.revertLeaving(source);

		assertNotSame(source, reverted, "reversion must copy, never mutate the caller's stack");
	}
}
