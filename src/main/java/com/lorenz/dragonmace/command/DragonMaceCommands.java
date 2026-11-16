package com.lorenz.dragonmace.command;

import com.lorenz.dragonmace.DragonMace;
import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

/**
 * The {@code /dm} command tree and its {@code /dragonmace} alias.
 *
 * <p>Registered from {@code Commands.<init>} instead of a Fabric API callback so the mod keeps its
 * loader + Minecraft only dependency set.
 */
public final class DragonMaceCommands {
	private DragonMaceCommands() {
	}

	/**
	 * Registers exactly five subcommands.
	 *
	 * <p>{@code /dm spawn}, {@code /dm delete}, {@code /dm transfer} and {@code /dm recover} were
	 * removed together with their handlers, argument definitions and tab completions, and no aliases
	 * were left behind. The root literal carries no permission requirement because {@code /dm status}
	 * and {@code /dm withdraw} are available to ordinary players; every administrative subcommand
	 * checks its own permission.
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dm")
				.then(Commands.literal("effect")
						.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(DragonMaceCommands::effect)))
				.then(Commands.literal("remove")
						.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
						.then(Commands.argument("player", GameProfileArgument.gameProfile())
								.executes(DragonMaceCommands::remove)))
				.then(Commands.literal("giveitem")
						.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions())
								&& source.getPlayer() != null)
						.then(Commands.literal("veryheavycore")
								.executes(DragonMaceCommands::giveVeryHeavyCore)))
				.then(Commands.literal("withdraw")
						.requires(source -> source.getPlayer() != null)
						.executes(DragonMaceCommands::withdraw))
				.then(Commands.literal("status")
						.executes(DragonMaceCommands::status));

		CommandNode<CommandSourceStack> dm = dispatcher.register(root);
		dispatcher.register(Commands.literal("dragonmace").redirect(dm));

		DragonMace.LOGGER.info(
				"Registered /dm and /dragonmace with effect, remove, giveitem, withdraw and status.");
	}

	/** {@code /dm effect <player>}: grants the Curse of the Dragons, respecting the single-owner rule. */
	private static int effect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		ServerPlayer target = EntityArgument.getPlayer(context, "player");

		switch (DragonMaceCurse.activate(server, target)) {
			case ACTIVATED -> {
				source.sendSuccess(() -> Component.literal("Gave the Curse of the Dragons to "
						+ target.getName().getString() + "."), true);
				return 1;
			}
			case ALREADY_ACTIVE -> {
				source.sendFailure(Component.literal(target.getName().getString()
						+ " already holds the Curse of the Dragons."));
				return 0;
			}
			case TAKEN -> {
				UUID holder = DragonMaceCurse.activeOwner(server);
				source.sendFailure(Component.literal("The Curse of the Dragons is already active on "
						+ describeOwner(server, holder) + ". Remove it first with /dm remove <player>."));
				return 0;
			}
			default -> {
				source.sendFailure(Component.literal("No player was supplied."));
				return 0;
			}
		}
	}

	/**
	 * {@code /dm remove <player>}: clears the active curse.
	 *
	 * <p>Uses a game profile argument, so an offline holder can be cleared from the saved data. Their
	 * leftover effects are cleaned up the next time they log in.
	 */
	private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, "player");

		if (profiles.isEmpty()) {
			source.sendFailure(Component.literal("No player was supplied."));
			return 0;
		}

		NameAndId profile = profiles.iterator().next();
		String name = profile.name() == null ? profile.id().toString() : profile.name();

		if (!DragonMaceCurse.remove(server, profile.id())) {
			source.sendFailure(Component.literal(name + " does not hold the Curse of the Dragons."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Removed the Curse of the Dragons from " + name + "."), true);
		return 1;
	}

	/** {@code /dm withdraw}: the active holder trades the curse back for one Very Heavy Core. */
	private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		ServerPlayer player = source.getPlayerOrException();

		if (!DragonMaceCurse.withdraw(server, player)) {
			source.sendFailure(Component.literal("You do not hold the Curse of the Dragons."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(
				"The Curse of the Dragons was withdrawn. A Very Heavy Core was returned to you."), false);
		return 1;
	}

	/** {@code /dm status}: shows the current holder, available to every player. */
	private static int status(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		UUID holder = DragonMaceCurse.activeOwner(server);

		if (holder == null) {
			source.sendSuccess(() -> Component.literal("The Curse of the Dragons is not active."), false);
			return 1;
		}

		String description = describeOwner(server, holder);
		source.sendSuccess(() -> Component.literal("The Curse of the Dragons is held by " + description + "."), false);
		return 1;
	}

	/**
	 * {@code /dm giveitem veryheavycore}: gives one Very Heavy Core to the executing player.
	 *
	 * <p>This only creates the item. It never activates the curse and never touches the global
	 * single-owner record, so the holder restriction is unchanged.
	 */
	private static int giveVeryHeavyCore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ItemStack stack = DragonMaceItems.createVeryHeavyCoreStack();

		if (!player.getInventory().add(stack)) {
			// A full inventory must never destroy the item.
			player.drop(stack, false);
		}

		source.sendSuccess(() -> Component.literal("Gave 1 Very Heavy Core."), true);
		return 1;
	}

	/** Resolves a UUID to a readable name, falling back to the raw UUID when the player is unknown. */
	private static String describeOwner(MinecraftServer server, UUID owner) {
		if (owner == null) {
			return "nobody";
		}

		ServerPlayer online = server.getPlayerList().getPlayer(owner);

		if (online != null) {
			return online.getName().getString();
		}

		return owner.toString();
	}
}
