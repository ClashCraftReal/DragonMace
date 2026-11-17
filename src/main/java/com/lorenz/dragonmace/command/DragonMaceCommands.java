package com.lorenz.dragonmace.command;

import com.lorenz.dragonmace.DragonMace;
import com.lorenz.dragonmace.DragonMaceCurse;
import com.lorenz.dragonmace.DragonMaceEnchantmentMenu;
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
import net.minecraft.world.SimpleMenuProvider;
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
						.executes(DragonMaceCommands::removeAll)
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
						.executes(DragonMaceCommands::status))
				.then(Commands.literal("changeenchants")
						.requires(source -> source.getPlayer() != null)
						.executes(DragonMaceCommands::openEnchantments));

		CommandNode<CommandSourceStack> dm = dispatcher.register(root);
		dispatcher.register(Commands.literal("dragonmace").redirect(dm));

		DragonMace.LOGGER.info(
				"Registered /dm and /dragonmace with effect, remove, giveitem, withdraw, status and changeenchants.");
	}

	/**
	 * {@code /dm changeenchants}: opens the global Draconic Mace enchantment configuration menu.
	 *
	 * <p>Only the active Curse of the Dragons holder may open it. The menu owns Confirm, Cancel and the
	 * server-wide cooldown, and re-checks ownership on every click, so this gate is the first of two.
	 */
	private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();

		if (!DragonMaceCurse.isActive(server, player.getUUID())) {
			source.sendFailure(Component.literal(
					"Only the holder of the Curse of the Dragons may change the mace enchantments."));
			return 0;
		}

		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, opener) -> new DragonMaceEnchantmentMenu(containerId, inventory),
				Component.literal("Draconic Mace Enchantments")));

		source.sendSuccess(() -> Component.literal("Opened the Draconic Mace enchantment menu."), false);
		return 1;
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
	 * {@code /dm remove}: clears the curse for every player, online and offline.
	 *
	 * <p>No player argument is needed and no player has to be online. The global owner is cleared
	 * first, then every online player is cleaned and every saved player file is purged.
	 */
	private static int removeAll(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		DragonMaceCurse.GlobalRemoval result = DragonMaceCurse.removeAll(source.getServer());

		source.sendSuccess(() -> Component.literal("Removed the Curse of the Dragons from every player: "
				+ result.cleaned() + " cleaned (" + result.online() + " online, " + result.purged()
				+ " saved files purged, " + result.alreadyClean() + " already clean), "
				+ result.failed() + " failed."), true);

		return result.failed() == 0 ? 1 : 0;
	}

	/**
	 * {@code /dm remove <player>}: kept for compatibility, and now performs the global removal too.
	 *
	 * <p>The profile argument still resolves a UUID for an offline player, so the old syntax keeps
	 * working, but the removal itself is global: every holder and every saved aura is cleared.
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
		DragonMaceCurse.GlobalRemoval result = DragonMaceCurse.removeAll(server);

		source.sendSuccess(() -> Component.literal("Removed the Curse of the Dragons from every player "
				+ "(requested for " + name + "): " + result.cleaned() + " cleaned (" + result.online()
				+ " online, " + result.purged() + " saved files purged, " + result.alreadyClean()
				+ " already clean), " + result.failed() + " failed."), true);

		return result.failed() == 0 ? 1 : 0;
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
