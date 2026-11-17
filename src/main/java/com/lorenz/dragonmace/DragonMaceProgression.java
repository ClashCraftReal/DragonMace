package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.lorenz.dragonmace.world.DragonMaceProgressionData;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * Kill progression, rune unlocks and the per-player rune selection.
 *
 * <p>Every rule here is a pure function of the player's kill count, so the whole system is testable
 * without a server. The live methods only supply the server, the player and the saved data.
 *
 * <p>Kills are counted once per death: {@link #beginDeath} claims the victim's UUID and
 * {@link #endDeath} releases it, so a second death event for the same victim in the same tick cannot
 * double-count. The count is only ever raised, which is what makes an unlock permanent.
 */
public final class DragonMaceProgression {
	/** Victims whose death has already been counted, so a repeated death event cannot double-count. */
	private static final Set<UUID> COUNTING = ConcurrentHashMap.newKeySet();

	private DragonMaceProgression() {
	}

	/** The outcome of one recorded kill. */
	public record KillResult(int kills, Optional<DragonMaceRune> unlocked) {
	}

	// ------------------------------------------------------------------ pure rules

	/** True when {@code stack} is a Draconic Mace, the only qualifying kill weapon. */
	public static boolean isQualifyingWeapon(ItemStack stack) {
		return DragonMaceIdentity.isDraconicMace(stack);
	}

	/** Every rune unlocked at {@code kills}, in declaration order. */
	public static List<DragonMaceRune> unlockedAt(int kills) {
		List<DragonMaceRune> unlocked = new ArrayList<>(DragonMaceRune.values().length);

		for (DragonMaceRune rune : DragonMaceRune.values()) {
			if (rune.isUnlockedAt(kills)) {
				unlocked.add(rune);
			}
		}

		return unlocked;
	}

	/** True when {@code rune} may be selected at {@code kills}. */
	public static boolean canSelect(DragonMaceRune rune, int kills) {
		return rune != null && rune.isUnlockedAt(kills);
	}

	/**
	 * The single rune unlocked by going from {@code killsBefore} to {@code killsAfter}.
	 *
	 * <p>Returns the lowest-threshold rune that crossed its requirement, so a jump of several kills still
	 * announces one rune at a time and never skips a message. Empty when nothing new was unlocked.
	 */
	public static Optional<DragonMaceRune> nextUnlock(int killsBefore, int killsAfter) {
		DragonMaceRune found = null;

		for (DragonMaceRune rune : DragonMaceRune.values()) {
			if (rune.unlockKills() > killsBefore && rune.unlockKills() <= killsAfter) {
				if (found == null || rune.unlockKills() < found.unlockKills()) {
					found = rune;
				}
			}
		}

		return Optional.ofNullable(found);
	}

	/** True when {@code selection} is legal at {@code kills}. */
	public static boolean isSelectionLegal(DragonMaceRuneSelection selection, int kills) {
		return DragonMaceRuneSelection.orDefaults(selection).isFullyUnlockedAt(kills);
	}

	// ------------------------------------------------------------------ saved state

	/** The player's kill count, or 0 when they have no record. */
	public static int kills(MinecraftServer server, UUID player) {
		return server == null || player == null ? 0 : DragonMaceProgressionData.get(server).kills(player);
	}

	/** The player's rune selection, sanitised against their own kill count. */
	public static DragonMaceRuneSelection runes(MinecraftServer server, UUID player) {
		return server == null || player == null
				? DragonMaceRuneSelection.defaults()
				: DragonMaceProgressionData.get(server).runes(player);
	}

	/** The player's rune toggles, all enabled when they have no record. */
	public static DragonMaceRuneToggles toggles(MinecraftServer server, UUID player) {
		return server == null || player == null
				? DragonMaceRuneToggles.defaults()
				: DragonMaceProgressionData.get(server).toggles(player);
	}

	/** True when {@code category} is enabled for {@code player}. */
	public static boolean isEnabled(MinecraftServer server, UUID player, DragonMaceRuneCategory category) {
		return category != null && toggles(server, player).get(category);
	}

	/**
	 * Saves one category's toggle for {@code player}.
	 *
	 * <p>Independent of the rune selection and of the enchantment cooldown: flipping a switch never
	 * changes which rune is selected and never starts a cooldown. On success the player's Draconic Maces
	 * are re-lored so the item shows the new state immediately.
	 *
	 * @return true when the stored value changed
	 */
	public static boolean saveToggles(MinecraftServer server, UUID player, DragonMaceRuneToggles toggles) {
		if (server == null || player == null) {
			return false;
		}

		DragonMaceProgressionData data = DragonMaceProgressionData.get(server);

		if (!data.setToggles(player, toggles)) {
			return false;
		}

		DragonMaceProgressionData.save(server);
		ServerPlayer online = server.getPlayerList().getPlayer(player);

		if (online != null) {
			refreshMaces(online);
		}

		return true;
	}

	/** Flips one category's toggle for {@code player}. */
	public static boolean toggleCategory(MinecraftServer server, UUID player,
			DragonMaceRuneCategory category) {
		if (server == null || player == null || category == null) {
			return false;
		}

		return saveToggles(server, player, toggles(server, player).toggled(category));
	}

	/**
	 * Saves a rune selection for {@code player}.
	 *
	 * <p>Refused when any selected rune is not unlocked, so a client cannot fake an unlock. On success
	 * the player's Draconic Maces are re-lored immediately.
	 *
	 * @return true when the selection was stored
	 */
	public static boolean saveRunes(MinecraftServer server, UUID player, DragonMaceRuneSelection selection) {
		if (server == null || player == null) {
			return false;
		}

		DragonMaceProgressionData data = DragonMaceProgressionData.get(server);
		int kills = data.kills(player);

		if (!isSelectionLegal(selection, kills)) {
			return false;
		}

		if (!data.setRunes(player, selection)) {
			return false;
		}

		DragonMaceProgressionData.save(server);
		ServerPlayer online = server.getPlayerList().getPlayer(player);

		if (online != null) {
			refreshMaces(online);
		}

		return true;
	}

	// ------------------------------------------------------------------ live kills

	/**
	 * Claims the victim's death so it can only be counted once.
	 *
	 * @return true when this call owns the death and should record the kill
	 */
	public static boolean beginDeath(UUID victim) {
		return victim != null && COUNTING.add(victim);
	}

	/** Releases a death claimed by {@link #beginDeath}. */
	public static void endDeath(UUID victim) {
		if (victim != null) {
			COUNTING.remove(victim);
		}
	}

	/**
	 * Records one qualifying kill for {@code player}.
	 *
	 * <p>The count is persisted before the announcement, and the player's maces are re-lored so the kill
	 * counter on the item is never stale.
	 */
	public static KillResult recordKill(MinecraftServer server, UUID player) {
		if (server == null || player == null) {
			return new KillResult(0, Optional.empty());
		}

		DragonMaceProgressionData data = DragonMaceProgressionData.get(server);
		int before = data.kills(player);
		int after = data.recordKill(player);
		DragonMaceProgressionData.save(server);

		Optional<DragonMaceRune> unlocked = nextUnlock(before, after);
		ServerPlayer online = server.getPlayerList().getPlayer(player);

		if (online != null) {
			refreshMaces(online);
		}

		unlocked.ifPresent(rune -> DragonMaceAnnouncements.runeUnlocked(server,
				online == null ? player.toString() : online.getName().getString(), rune));

		return new KillResult(after, unlocked);
	}

	/**
	 * Counts a player kill made with the Draconic Mace.
	 *
	 * <p>Called after the curse transfer, so the killer's mace has already been converted and the kill
	 * that grants the curse also counts. A self-kill, an uncredited death and a kill made with anything
	 * other than a Draconic Mace are all ignored.
	 */
	public static Optional<DragonMaceRune> registerKill(MinecraftServer server, ServerPlayer victim,
			DamageSource source) {
		if (server == null || victim == null || source == null) {
			return Optional.empty();
		}

		ServerPlayer killer = DragonMaceDeathTransfer.resolveKiller(victim, source);

		if (killer == null || killer.getUUID().equals(victim.getUUID())) {
			return Optional.empty();
		}

		if (!isQualifyingWeapon(killer.getMainHandItem())) {
			return Optional.empty();
		}

		KillResult result = recordKill(server, killer.getUUID());

		// The Kill rune effect runs after the count is persisted, so the effect and the item lore agree
		// about the kill that just happened. onKill re-checks the KILL toggle itself, so a disabled
		// category still counts the kill but plays nothing.
		DragonMaceRuneEffects.onKill(killer, victim, runes(server, killer.getUUID()).kill());

		return result.unlocked();
	}

	// ------------------------------------------------------------------ lore

	/** The lore context of {@code player}: live enchantments, saved runes and saved kills. */
	public static DragonMaceLoreContext loreContext(MinecraftServer server, ServerPlayer player,
			RegistryAccess registries) {
		if (player == null) {
			return DragonMaceLoreContext.defaults(registries);
		}

		return new DragonMaceLoreContext(
				DragonMaceEnchantments.configured(registries, DragonMaceConfig.get()),
				DragonMaceLoreContext.summaryOf(
						DragonMaceEnchantments.configured(registries, DragonMaceConfig.get())),
				runes(server, player.getUUID()),
				kills(server, player.getUUID()),
				toggles(server, player.getUUID()));
	}

	/** True when the player carries at least one Draconic Mace. */
	public static boolean hasDraconicMace(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		var inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (DragonMaceIdentity.isDraconicMace(inventory.getItem(slot))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Rebuilds the name, lore and enchantments of every Draconic Mace the player carries.
	 *
	 * <p>Idempotent and in place: each stack is rewritten at its own slot, so nothing is moved,
	 * duplicated or deleted. Called after a kill, after a rune confirmation, and whenever the curse is
	 * applied, so the item always reflects the current progression.
	 *
	 * @return the number of stacks refreshed
	 */
	public static int refreshMaces(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return 0;
		}

		DragonMaceLoreContext context = loreContext(server, player, server.registryAccess());
		var inventory = player.getInventory();
		int refreshed = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!DragonMaceIdentity.isDraconicMace(stack)) {
				continue;
			}

			DragonMaceItems.refreshDraconicMace(stack, context);
			refreshed++;
		}

		return refreshed;
	}
}
