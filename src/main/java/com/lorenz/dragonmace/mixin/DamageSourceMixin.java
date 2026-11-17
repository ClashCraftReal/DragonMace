package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.DragonMaceDeathMessage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replays the captured Draconic Mace name when a death message is built.
 *
 * <p>Vanilla resolves the weapon by reading the killer's live main hand here, which is exactly the
 * read the curse transfer can invalidate. When a Draconic snapshot applies, the message is rebuilt with
 * the same translation key and the same three arguments vanilla uses, so the wording and formatting are
 * identical and only the weapon name is restored.
 */
@Mixin(DamageSource.class)
public class DamageSourceMixin {
	@Inject(method = "getLocalizedDeathMessage", at = @At("HEAD"), cancellable = true)
	private void dragonmace$useCapturedDraconicWeapon(LivingEntity victim,
			CallbackInfoReturnable<Component> cir) {
		DamageSource source = (DamageSource) (Object) this;

		DragonMaceDeathMessage.weaponFor(victim, source).ifPresent(weapon -> {
			Entity causing = source.getEntity();

			if (causing == null) {
				return;
			}

			cir.setReturnValue(DragonMaceDeathMessage.buildMessage(source.getMsgId(),
					victim.getDisplayName(), causing.getDisplayName(), weapon));
		});
	}
}
