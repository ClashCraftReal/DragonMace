package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMace;
import com.lorenz.dragonmace.DragonMaceRecipes;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites the recipe map while the recipe manager builds it: the vanilla mace recipe is dropped and
 * the Dragon Mace recipe is added. Every other recipe is left untouched, and the mace item itself
 * keeps working normally.
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
	@ModifyVariable(
			method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private RecipeMap dragonmace$rewriteRecipes(RecipeMap recipes) {
		DragonMace.LOGGER.info("Disabled the vanilla mace crafting recipe ({})", DragonMaceRecipes.VANILLA_MACE_RECIPE);
		return DragonMaceRecipes.apply(recipes);
	}
}
