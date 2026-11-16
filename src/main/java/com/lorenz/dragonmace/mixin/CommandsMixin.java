package com.lorenz.dragonmace.mixin;

import com.lorenz.dragonmace.command.DragonMaceCommands;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the Dragon Mace commands on the vanilla dispatcher.
 *
 * <p>Fabric's {@code CommandRegistrationCallback} lives in Fabric API, which this mod deliberately
 * does not depend on, so the dispatcher is extended at the end of the {@code Commands} constructor.
 */
@Mixin(Commands.class)
public class CommandsMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void dragonmace$registerCommands(Commands.CommandSelection selection, CommandBuildContext context,
			CallbackInfo ci) {
		DragonMaceCommands.register(((Commands) (Object) this).getDispatcher());
	}
}
