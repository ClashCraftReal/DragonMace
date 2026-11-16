package com.lorenz.dragonmace.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the crafting player of a result slot.
 *
 * <p>{@code Slot.onQuickCraft} carries no player argument, so the shift-click craft path reads the
 * owner from the slot itself instead of guessing it.
 */
@Mixin(ResultSlot.class)
public interface ResultSlotAccessor {
	@Accessor("player")
	Player dragonmace$getPlayer();
}
