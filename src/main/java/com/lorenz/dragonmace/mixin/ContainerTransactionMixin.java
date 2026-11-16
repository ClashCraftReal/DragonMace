package com.lorenz.dragonmace.mixin;

import java.util.Set;

import com.lorenz.dragonmace.DragonMaceProtection;
import com.lorenz.dragonmace.DragonMaceTotemLimit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects Dragon Mace container transactions before vanilla mutates the source stack.
 *
 * <p>1.21.11 {@code AbstractContainerMenu.doClick} splits or clears the source before it writes the
 * destination. Cancelling only {@code Slot.set} therefore deletes the mace: the destination write is
 * refused, but the source is already empty. This hook validates the destination at the transaction
 * entry point instead, so a refused click leaves the carried or clicked stack untouched.
 *
 * <p>Shift-click reaches {@code moveItemStackTo}, which splits the live source stack before calling
 * {@code Slot.setByPlayer}. Its head is guarded separately. These two points cover pickup, drag,
 * number-key swaps, shift-click, bundles, hoppers and every custom menu that uses the vanilla
 * transaction primitives.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerTransactionMixin {
	@Shadow
	@Final
	private Set<Slot> quickcraftSlots;

	@Shadow
	private int quickcraftStatus;

	@Shadow
	protected abstract void resetQuickCraft();

	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void dragonmace$rejectProtectedClick(int slotIndex, int button, ClickType clickType, Player player,
			CallbackInfo ci) {
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

		switch (clickType) {
			case PICKUP -> {
				// slotIndex -999 is the drop-outside action, which is a real drop and must work.
				if (slotIndex >= 0 && slotIndex < menu.slots.size()
						&& DragonMaceProtection.blocksSlotStorage(menu.getSlot(slotIndex), menu.getCarried())) {
					ci.cancel();
				}
			}
			case SWAP -> {
				// The hotbar/offhand stack is what vanilla writes into the clicked slot.
				if (slotIndex >= 0 && slotIndex < menu.slots.size() && (button >= 0 && button < 9 || button == 40)) {
					ItemStack swappedIn = player.getInventory().getItem(button);

					if (DragonMaceProtection.blocksSlotStorage(menu.getSlot(slotIndex), swappedIn)) {
						ci.cancel();
					}
				}
			}
			case QUICK_CRAFT -> dragonmace$rejectProtectedDrag(menu, slotIndex, ci);
			default -> {
			}
		}
	}

	private void dragonmace$rejectProtectedDrag(AbstractContainerMenu menu, int slotIndex, CallbackInfo ci) {
		ItemStack carried = menu.getCarried();

		if (!DragonMaceProtection.isProtectedStack(carried)) {
			return;
		}

		if (this.quickcraftStatus == 1) {
			// Slots are collected one at a time; reject a protected destination before it is added.
			if (slotIndex >= 0 && slotIndex < menu.slots.size()
					&& DragonMaceProtection.blocksSlotStorage(menu.getSlot(slotIndex), carried)) {
				this.resetQuickCraft();
				ci.cancel();
			}
		} else if (this.quickcraftStatus == 2) {
			// Commit: every selected destination is validated before the carried stack is reduced.
			for (Slot slot : this.quickcraftSlots) {
				if (DragonMaceProtection.blocksSlotStorage(slot, carried)) {
					this.resetQuickCraft();
					ci.cancel();
					return;
				}
			}
		}
	}

	@Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
	private void dragonmace$rejectProtectedQuickMove(ItemStack stack, int start, int end, boolean reverse,
			CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

		if (DragonMaceProtection.blocksQuickMove(menu.slots, start, end, stack)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Tail net for Dragons Pride.
	 *
	 * <p>Every click that can move a totem into the player inventory ends here, including shift-click,
	 * number-key swaps and drags that never reach {@code Inventory.add}. The excess is dropped rather
	 * than deleted, so a player never loses a totem to the limit.
	 */
	@Inject(method = "doClick", at = @At("TAIL"))
	private void dragonmace$enforceTotemLimit(int slotIndex, int button, ClickType clickType, Player player,
			CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			DragonMaceTotemLimit.enforce(serverPlayer);
		}
	}
}
