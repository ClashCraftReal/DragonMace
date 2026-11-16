package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceDeathTransfer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the kill transfer before the dying player's inventory is dropped or cleared.
 *
 * <p>{@code ServerPlayer.die} calls {@code dropAllDeathLoot} directly and does not call
 * {@code super.die}, so this head injection is the one point that sees every player death before
 * {@code Inventory.dropAll}, {@code EntityEquipment.dropAll}, keepInventory copying, curse handling
 * and the respawn discard. Once the mace has been moved out, every later death path is a no-op for
 * it.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void dragonmace$transferBeforeDeathDrops(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer victim = (ServerPlayer) (Object) this;
		DragonMaceDeathTransfer.transfer(victim.level().getServer(), victim, damageSource);
	}

	/**
	 * Moves the curse to the killer at the same head point as the mace transfer.
	 *
	 * <p>Running before {@code dropAllDeathLoot} means the victim's effects are cleared and the
	 * killer's are applied while both inventories are still intact, so the Draconic Mace conversion
	 * sees the killer's real inventory. A death without a credited player killer keeps the curse on
	 * the victim, and {@code restoreFrom} re-applies it on respawn.
	 */
	@Inject(method = "die", at = @At("HEAD"))
	private void dragonmace$transferCurseBeforeDeathDrops(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer victim = (ServerPlayer) (Object) this;
		DragonMaceCurse.handleDeath(victim.level().getServer(), victim, damageSource);
	}
}
