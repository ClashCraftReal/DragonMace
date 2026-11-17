package com.lorenz.dragonmace.mixin;

import java.util.UUID;

import com.lorenz.dragonmace.DragonMaceIdentity;
import com.lorenz.dragonmace.DragonMaceMaceConversion;
import com.lorenz.dragonmace.DragonMacePickup;
import com.lorenz.dragonmace.DragonMaceProtection;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the Dragon Mace ground protections to every item entity that receives the stack, enforces
 * the temporary kill-transfer reservation, and records persistent ownership after a successful
 * pickup.
 *
 * <p>{@code ItemEntity.setItem} is used by the constructor, by loading from disk and by merges, so
 * the protection hook covers Q drops, drag-out drops, death drops, respawns and entities that
 * already exist in an old world. The entity becomes non-despawning and invulnerable, which makes it
 * immune to fire, lava, explosions and other damage without changing the item itself.
 */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Shadow
	private UUID target;

	@Inject(method = "setItem", at = @At("TAIL"))
	private void dragonmace$protectDroppedMace(ItemStack stack, CallbackInfo ci) {
		DragonMaceProtection.protectItemEntity((ItemEntity) (Object) this);
	}

	/**
	 * Makes a protected dropped stack fire and lava immune.
	 *
	 * <p>{@code ItemEntity.fireImmune()} is the gate {@code Entity.lavaHurt} consults before it applies
	 * lava damage and sets the entity alight, so returning {@code true} here stops both. Invulnerability
	 * already blocks the damage through {@code hurtServer}, but without this the entity still caught fire
	 * and played the lava hurt sound. Void destruction is a position check, not damage, so a protected
	 * stack still disappears below the world.
	 */
	@Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
	private void dragonmace$fireImmune(CallbackInfoReturnable<Boolean> cir) {
		ItemEntity entity = (ItemEntity) (Object) this;

		if (DragonMaceProtection.isFireImmune(entity.getItem())) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Reverts a Draconic Mace the moment it becomes a ground item.
	 *
	 * <p>A drop is the other way a mace leaves the cursed player's inventory: {@code Player.drop}
	 * removes the stack from the inventory and hands it to a new item entity. Reverting the argument
	 * before it is stored means the entity carries a normal mace, so the item is neither lost nor
	 * duplicated and a later pickup by the holder converts it again.
	 */
	@ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack dragonmace$revertLeavingMace(ItemStack stack) {
		return DragonMaceMaceConversion.revertLeaving(stack);
	}

	/**
	 * Explicitly enforces the temporary reservation. Vanilla already checks {@code target}, but this
	 * keeps the rule in the mod and gives the reserved-drop behavior one testable owner.
	 */
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void dragonmace$enforceReservation(Player player, CallbackInfo ci) {
		ItemEntity entity = (ItemEntity) (Object) this;

		if (!DragonMaceIdentity.isDragonMace(entity.getItem())) {
			return;
		}

		if (!DragonMacePickup.canPickUp(this.target, player.getUUID())) {
			ci.cancel();
		}
	}

	/**
	 * A successful pickup is the only path where {@code playerTouch} discards this entity while the
	 * marked stack is still present (vanilla empties it for the discard and restores the count right
	 * afterwards). A failed pickup leaves the entity alive, so ownership is never touched.
	 */
	@Inject(method = "playerTouch", at = @At("TAIL"))
	private void dragonmace$claimAfterPickup(Player player, CallbackInfo ci) {
		ItemEntity entity = (ItemEntity) (Object) this;

		if (!entity.isRemoved()) {
			return;
		}

		DragonMacePickup.completePickup(entity.level().getServer(), player, entity);
	}
}
