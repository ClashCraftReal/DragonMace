package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceDeathMessage;
import com.lorenz.dragonmace.DragonMaceDeathTransfer;

import net.minecraft.server.MinecraftServer;
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
 *
 * <p>The three steps run in one injection so their order is fixed rather than left to mixin
 * application order: the weapon name is snapshotted, the mace and then the curse are transferred, and
 * the weapon name is snapshotted again. The second snapshot is what sees the killer's mace after the
 * curse has converted it, and the first is what survives a mace that the transfer reverts. The
 * transfer timing itself is unchanged: still at the head of {@code die}, still before the death drops.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void dragonmace$handleDeath(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer victim = (ServerPlayer) (Object) this;
		MinecraftServer server = victim.level().getServer();

		// Before the transfer: keeps the Draconic name if the transfer reverts the killer's mace.
		DragonMaceDeathMessage.capture(victim, damageSource);

		DragonMaceDeathTransfer.transfer(server, victim, damageSource);

		// Moves the curse to the killer at the same head point as the mace transfer. Running before
		// dropAllDeathLoot means the victim's effects are cleared and the killer's are applied while
		// both inventories are still intact, so the Draconic Mace conversion sees the killer's real
		// inventory. A death without a credited player killer keeps the curse on the victim, and
		// restoreFrom re-applies it on respawn.
		DragonMaceCurse.handleDeath(server, victim, damageSource);

		// After the transfer: sees the killer's mace once the curse has converted it.
		DragonMaceDeathMessage.capture(victim, damageSource);
	}

	/** Drops the snapshot once the death message has been built, so nothing is retained. */
	@Inject(method = "die", at = @At("RETURN"))
	private void dragonmace$clearCapturedWeapon(DamageSource damageSource, CallbackInfo ci) {
		DragonMaceDeathMessage.clear(((ServerPlayer) (Object) this).getUUID());
	}
}
