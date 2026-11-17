package com.lorenz.dragonmace.mixin;

import java.util.Set;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceProtection;
import com.lorenz.dragonmace.DragonMaceTotemLimit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
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
	public abstract ItemStack getCarried();

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
					dragonmace$cancelAndResync(menu, player, ci);
				}
			}
			case SWAP -> {
				// The hotbar/offhand stack is what vanilla writes into the clicked slot.
				if (slotIndex >= 0 && slotIndex < menu.slots.size()
						&& DragonMaceProtection.blocksSwapInto(menu.getSlot(slotIndex), button,
								player.getInventory().getItem(button))) {
					dragonmace$cancelAndResync(menu, player, ci);
				}
			}
			case QUICK_CRAFT -> dragonmace$rejectProtectedDrag(menu, player, slotIndex, ci);
			default -> {
			}
		}
	}

	/**
	 * Cancels the click and forces a full menu resync.
	 *
	 * <p>The client predicts a click locally before the server answers. Cancelling alone leaves that
	 * prediction on screen, so a refused offhand swap looked like the egg had vanished even though the
	 * server still held it. {@code sendAllDataToRemote} replaces the client's copy with the real
	 * server state.
	 */
	private static void dragonmace$cancelAndResync(AbstractContainerMenu menu, Player player, CallbackInfo ci) {
		ci.cancel();

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.containerMenu.sendAllDataToRemote();
		}
	}

	private void dragonmace$rejectProtectedDrag(AbstractContainerMenu menu, Player player, int slotIndex,
			CallbackInfo ci) {
		ItemStack carried = menu.getCarried();

		if (!DragonMaceProtection.isProtectedStack(carried)) {
			return;
		}

		if (this.quickcraftStatus == 1) {
			// Slots are collected one at a time; reject a protected destination before it is added.
			if (slotIndex >= 0 && slotIndex < menu.slots.size()
					&& DragonMaceProtection.blocksSlotStorage(menu.getSlot(slotIndex), carried)) {
				this.resetQuickCraft();
				dragonmace$cancelAndResync(menu, player, ci);
			}
		} else if (this.quickcraftStatus == 2) {
			// Commit: every selected destination is validated before the carried stack is reduced.
			for (Slot slot : this.quickcraftSlots) {
				if (DragonMaceProtection.blocksSlotStorage(slot, carried)) {
					this.resetQuickCraft();
					dragonmace$cancelAndResync(menu, player, ci);
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
	 * Refuses a totem placement that would break Dragons Pride.
	 *
	 * <p>Runs at the head of the click, before anything is moved, so a rejected transfer leaves both the
	 * cursor and the slot untouched. Nothing is ever dropped to satisfy the limit. Number-key and offhand
	 * swaps move a totem between two player-inventory slots, so they cannot raise the count and are left
	 * alone.
	 */
	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void dragonmace$rejectTotemInsert(int slotIndex, int button, ClickType clickType, Player player,
			CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_CRAFT) {
			return;
		}

		// Dragons Pride only exists while the curse is held. Without this gate every player, including
		// one who withdrew or transferred the curse away, had totem clicks refused once they carried
		// two, which is why totems could not be moved without the curse.
		MinecraftServer server = serverPlayer.level().getServer();

		if (server == null) {
			return;
		}

		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		ItemStack carried = this.getCarried();

		if (!DragonMaceTotemLimit.isTotem(carried)) {
			return;
		}

		if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
			return;
		}

		if (!(menu.getSlot(slotIndex).container instanceof Inventory inventory)) {
			return;
		}

		if (DragonMaceTotemLimit.blocksInsert(DragonMaceCurse.activeOwner(server), serverPlayer.getUUID(),
				DragonMaceTotemLimit.countTotems(inventory), carried)) {
			dragonmace$cancelAndResync(menu, player, ci);
		}
	}

	/**
	 * Refuses a shift-click that would carry a totem from a container into the player inventory.
	 *
	 * <p>{@code quickMoveStack} is abstract on {@code AbstractContainerMenu}, so it cannot be injected.
	 * {@code moveItemStackTo} is the concrete funnel every shift-click ends in, and it runs before the
	 * source stack is split, so a refused move leaves the source untouched. The source stack is excluded
	 * from the count by identity, so a shift-click that starts inside the player inventory is an
	 * internal move and is never blocked.
	 */
	@Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
	private void dragonmace$rejectTotemQuickMove(ItemStack stack, int start, int end, boolean reverse,
			CallbackInfoReturnable<Boolean> cir) {
		if (!DragonMaceTotemLimit.isTotem(stack)) {
			return;
		}

		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

		for (int index = start; index < end && index < menu.slots.size(); index++) {
			if (!(menu.slots.get(index).container instanceof Inventory inventory)) {
				continue;
			}

			if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
				return;
			}

			MinecraftServer server = serverPlayer.level().getServer();

			if (server == null || !DragonMaceCurse.isActive(server, serverPlayer.getUUID())) {
				return;
			}

			if (DragonMaceTotemLimit.blocksQuickMove(inventory, stack)) {
				cir.setReturnValue(false);
			}

			return;
		}
	}
}
