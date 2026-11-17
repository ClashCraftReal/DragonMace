package com.lorenz.dragonmace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Server-wide, server-generated announcements for the unique Dragon Mace.
 *
 * <p>Every live method is called only after the corresponding operation has actually succeeded. The
 * message builders are separate and pure so the exact wording can be tested without a running
 * server. All messages use the same colour, which matches the Dragon Mace's dark-purple item name.
 */
public final class DragonMaceAnnouncements {
	/** 🐉 */
	private static final String DRAGON = "\uD83D\uDC09";
	/** ⚔️ */
	private static final String SWORDS = "\u2694\uFE0F";

	private DragonMaceAnnouncements() {
	}

	public static String craftedMessage(String playerName) {
		return DRAGON + " THE DRAGON MACE HAS BEEN FORGED! " + playerName
				+ " has crafted the Dragon Mace!";
	}

	public static String claimedMessage(String playerName) {
		return DRAGON + " THE DRAGON MACE HAS BEEN CLAIMED! " + playerName
				+ " has claimed the Dragon Mace!";
	}

	public static String changedHandsMessage(String playerName) {
		return SWORDS + " THE DRAGON MACE CHANGES HANDS! " + playerName
				+ " is now the wielder of the Dragon Mace!";
	}

	public static String combatClaimedMessage(String killerName, String victimName) {
		return SWORDS + " THE DRAGON MACE HAS BEEN CLAIMED IN COMBAT! " + killerName
				+ " has defeated " + victimName + " and claimed the Dragon Mace!";
	}

	public static String respawnedMessage(String locationDescription) {
		return DRAGON + " THE DRAGON MACE HAS RETURNED! The Dragon Mace has been summoned at "
				+ locationDescription + "!";
	}

	public static String sealedMessage() {
		return DRAGON + " THE DRAGON MACE HAS BEEN SEALED! An operator has removed the Dragon Mace "
				+ "from the world.";
	}

	public static String lostToVoidMessage() {
		return DRAGON + " THE DRAGON MACE HAS BEEN LOST! The Dragon Mace has fallen into the void and "
				+ "been destroyed. An operator must summon it again.";
	}

	/** The exact prefix required for every Curse of the Dragons announcement. */
	public static final String PREFIX = "[Dragon Mace] ";

	public static String coreCraftedMessage() {
		return PREFIX + "A Very Heavy Core has been crafted!";
	}

	public static String coreConsumedMessage(String playerName) {
		return PREFIX + playerName + " has consumed a Very Heavy Core!";
	}

	public static String curseTransferredMessage(String victimName, String killerName) {
		return PREFIX + "The Curse of the Dragons has transferred from " + victimName + " to " + killerName + "!";
	}

	public static void coreCrafted(MinecraftServer server) {
		broadcast(server, coreCraftedMessage());
	}

	public static void coreConsumed(MinecraftServer server, String playerName) {
		broadcast(server, coreConsumedMessage(playerName));
	}

	public static void curseTransferred(MinecraftServer server, String victimName, String killerName) {
		broadcast(server, curseTransferredMessage(victimName, killerName));
	}

	public static void crafted(MinecraftServer server, String playerName) {
		broadcast(server, craftedMessage(playerName));
	}

	public static void claimed(MinecraftServer server, String playerName) {
		broadcast(server, claimedMessage(playerName));
	}

	public static void changedHands(MinecraftServer server, String playerName) {
		broadcast(server, changedHandsMessage(playerName));
	}

	public static void combatClaimed(MinecraftServer server, String killerName, String victimName) {
		broadcast(server, combatClaimedMessage(killerName, victimName));
	}

	public static void respawned(MinecraftServer server, String locationDescription) {
		broadcast(server, respawnedMessage(locationDescription));
	}

	public static void sealed(MinecraftServer server) {
		broadcast(server, sealedMessage());
	}

	public static void lostToVoid(MinecraftServer server) {
		broadcast(server, lostToVoidMessage());
	}

	/**
	 * Announces a newly unlocked rune.
	 *
	 * <p>The rune's own name carries its assigned colour, so the announcement matches the GUI and the
	 * item lore. Broadcast to every online player, like every other announcement.
	 */
	public static void runeUnlocked(MinecraftServer server, String playerName, DragonMaceRune rune) {
		if (server == null || rune == null) {
			return;
		}

		Component message = Component.empty()
				.append(Component.literal((playerName == null ? "A player" : playerName) + " unlocked the ")
						.withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal(rune.displayName()).withStyle(rune.colour()))
				.append(Component.literal(" rune.").withStyle(ChatFormatting.LIGHT_PURPLE));

		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	/**
	 * Sends one message to every online player. Kept in one place so no event can accidentally use a
	 * different audience or a second duplicate hook.
	 */
	public static void broadcast(MinecraftServer server, String message) {
		if (server == null || message == null) {
			return;
		}

		server.getPlayerList().broadcastSystemMessage(
				Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE), false);
	}
}
