package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMace;
import com.lorenz.dragonmace.DragonMaceCrafting;
import com.lorenz.dragonmace.DragonMaceRecipes;
import com.lorenz.dragonmace.world.DragonMaceWorldData;

import java.util.Optional;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rewrites the recipe map while the recipe manager builds it: the vanilla mace recipe is dropped and
 * the Dragon Mace recipe is added. Every other recipe is left untouched, and the mace item itself
 * keeps working normally.
 *
 * <p>It also hides the Dragon Mace recipe from the crafting menu once the world already has a mace.
 * Suppressing the match here leaves the result slot empty, which is what makes a second craft
 * impossible instead of merely uncollectable.
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

	@Inject(
			method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
			at = @At("RETURN"),
			cancellable = true
	)
	private void dragonmace$hideRecipeOnceCrafted(RecipeType<?> type, RecipeInput input, Level level,
			RecipeHolder<?> preferred, CallbackInfoReturnable<Optional<RecipeHolder<?>>> cir) {
		Optional<RecipeHolder<?>> found = cir.getReturnValue();

		if (found == null || found.isEmpty() || !DragonMaceRecipes.isDragonMaceRecipe(found.get())) {
			return;
		}

		MinecraftServer server = level == null ? null : level.getServer();

		if (server == null) {
			return;
		}

		if (!DragonMaceCrafting.canCraft(DragonMaceWorldData.get(server).getState())) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
