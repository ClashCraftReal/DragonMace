package com.lorenz.dragonmace;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The custom tablist tag that identifies the single Curse of the Dragons holder.
 *
 * <p>The tag is written as the player's tab list display name and pushed to every online client with
 * {@code ClientboundPlayerInfoUpdatePacket}. It is never written into the player's game profile, so
 * the normal username, chat name and scoreboard identity stay untouched.
 */
public final class DragonMaceTabList {
	/** The visible tag, e.g. {@code [Curse of the Dragons]}. */
	public static final String TAG_TEXT = "[Curse of the Dragons]";

	private DragonMaceTabList() {
	}

	/** The styled tag component, purple text with a gold bracket pair. */
	public static Component tag() {
		MutableComponent open = Component.literal("[").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
		MutableComponent body = Component.literal("Curse of the Dragons")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE));
		MutableComponent close = Component.literal("]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));

		return Component.empty().append(open).append(body).append(close);
	}

	/** The full display name: the tag followed by the player's own name. */
	public static Component displayName(ServerPlayer player) {
		return Component.empty()
				.append(tag())
				.append(Component.literal(" ").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
				.append(Component.literal(player.getName().getString())
						.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
	}

	/** Applies the tag and synchronises it to every online player. */
	public static void apply(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}

		broadcast(server, player);
	}

	/** Removes the tag and synchronises the plain name to every online player. */
	public static void clear(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}

		broadcast(server, player);
	}

	/**
	 * Pushes the current display name of one player to every client.
	 *
	 * <p>Only {@code UPDATE_DISPLAY_NAME} is sent, so the rest of the tab list entry is untouched. The
	 * value the packet carries comes from {@code ServerPlayer.getTabListDisplayName()}, which
	 * {@code ServerPlayerTabListMixin} overrides for the curse holder.
	 */
	private static void broadcast(MinecraftServer server, ServerPlayer player) {
		ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
				EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(player));

		server.getPlayerList().broadcastAll(packet);
	}
}
