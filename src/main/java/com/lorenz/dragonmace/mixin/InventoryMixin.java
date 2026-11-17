package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceIdentity;
import com.lorenz.dragonmace.DragonMaceMaceConversion;
import com.lorenz.dragonmace.DragonMaceRecipeUnlock;
import com.lorenz.dragonmace.DragonMaceTotemLimit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses a Totem of Undying insertion that would break Dragons Pride.
 *
 * <p>{@code Inventory.add} is the single funnel for item pickups and for every insert that has no
 * explicit slot, so refusing here stops the third totem at the source instead of dropping it again
 * after it was already picked up.
 */
@Mixin(Inventory.class)
public class InventoryMixin {
	@Shadow
	@Final
	public Player player;

	@Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void dragonmace$limitTotems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!DragonMaceTotemLimit.isTotem(stack)) {
			return;
		}

		if (!(this.player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		MinecraftServer server = serverPlayer.level().getServer();

		if (server == null || !DragonMaceCurse.isActive(server, serverPlayer.getUUID())) {
			return;
		}

		if (!DragonMaceTotemLimit.allowsAdd(serverPlayer.getInventory(), stack)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Unlocks the Very Heavy Core recipe when a Dragon Egg actually enters the inventory.
	 *
	 * <p>Runs at {@code RETURN} and only when {@code add} reported success, so a refused insertion
	 * never unlocks the recipe. This covers pickups, crafting results and every slot-less insert.
	 */
	@Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private void dragonmace$unlockCoreRecipeOnEgg(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue()) || !DragonMaceIdentity.isDragonEgg(stack)) {
			return;
		}

		if (this.player instanceof ServerPlayer serverPlayer) {
			DragonMaceRecipeUnlock.unlockVeryHeavyCore(serverPlayer);
		}
	}

	/**
	 * Brings the inventory in line with the curse state after any explicit slot write.
	 *
	 * <p>{@code Inventory.setItem} is the funnel for every explicit slot write: shift-click results,
	 * crafting output, anvil output, drag and drop and number-key swaps. Reconciling here converts a
	 * mace that arrives and reverts one that is no longer wanted, and a move between two inventory
	 * slots keeps the mace Draconic because it never leaves the inventory.
	 */
	@Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
	private void dragonmace$reconcileOnSetItem(int slot, ItemStack stack, CallbackInfo ci) {
		dragonmace$reconcile();
	}

	/**
	 * Brings the inventory in line with the curse state after a slot-less insert.
	 *
	 * <p>{@code add} writes straight into the backing list instead of going through {@code setItem}, so
	 * the hook above never sees a pickup. The whole inventory is reconciled because {@code add} does not
	 * report which slot it used; the sweep is idempotent and guarded against re-entry.
	 */
	@Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private void dragonmace$reconcileOnAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}

		dragonmace$reconcile();
	}

	/** Reconciles this inventory when it belongs to a server player. */
	private void dragonmace$reconcile() {
		if (!(this.player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		MinecraftServer server = serverPlayer.level().getServer();

		if (server == null) {
			return;
		}

		DragonMaceMaceConversion.reconcile(serverPlayer, server.registryAccess());
		// Dragons Pride: any totem that arrived through a path the click guards cannot see is dropped.
		DragonMaceTotemLimit.enforce(serverPlayer);
	}
}
