package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceAnnouncements;
import com.lorenz.dragonmace.DragonMaceCrafting;
import com.lorenz.dragonmace.DragonMaceIdentity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the world uniqueness flag at the moment a Dragon Mace is actually taken out of a crafting
 * result slot, which is the point where the craft has succeeded.
 *
 * <p>This covers the click and number-key paths, which hand the marked stack to {@code onTake}. The
 * shift-click path empties the result slot first and reports the original stack through
 * {@code Slot.onQuickCraft}, so it is handled in {@link SlotMixin} instead.
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "onTake", at = @At("HEAD"))
	private void dragonmace$markCrafted(Player player, ItemStack stack, CallbackInfo ci) {
		if (!DragonMaceIdentity.isDragonMace(stack)) {
			return;
		}

		DragonMaceCrafting.completeCraft(this.player.level().getServer(), this.player);
	}

	/**
	 * Announces a crafted Very Heavy Core exactly once per craft.
	 *
	 * <p>{@code checkTakeAchievements} is called by {@code onTake} for a click or number-key take and
	 * by {@code onQuickCraft} for a shift-click take, and the two are mutually exclusive for a single
	 * craft, so this is the one point that sees both paths without double-announcing.
	 */
	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void dragonmace$announceCoreCrafted(ItemStack stack, CallbackInfo ci) {
		if (!DragonMaceIdentity.isVeryHeavyCore(stack)) {
			return;
		}

		DragonMaceAnnouncements.coreCrafted(this.player.level().getServer());
	}
}
