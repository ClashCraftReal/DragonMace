package com.lorenz.dragonmace;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Preserves the Draconic Mace identity for the victim's death message.
 *
 * <p>{@code CombatTracker.getDeathMessage} runs inside {@code ServerPlayer.die}, after the curse has
 * already moved to the killer, and it resolves the weapon by reading the killer's <em>live</em> main
 * hand through {@code DamageSource.getLocalizedDeathMessage}. Anything the transfer does to that stack
 * therefore changes the message. This class snapshots the weapon name around the transfer and replays
 * it when the message is built, so the message is correct no matter what the transfer did to the item.
 *
 * <p>Only a Draconic Mace overrides the message. An ordinary mace, another weapon or an unrelated
 * damage source keeps vanilla's own wording, and the override additionally requires the damage to be
 * credited to the same player the snapshot was taken from, so an indirect kill can never be attributed
 * to a weapon that player was not holding.
 */
public final class DragonMaceDeathMessage {
	/** The stack carries no name worth preserving. */
	public static final int RANK_NONE = 0;
	/** The stack has a custom name but is not Draconic. */
	public static final int RANK_NAMED = 1;
	/** The stack is a Draconic Mace, live or reverted. */
	public static final int RANK_DRACONIC = 2;

	/** One captured weapon, with the player it was taken from. */
	public record Captured(UUID killer, Component weapon, int rank) {
	}

	private static final Map<UUID, Captured> CAPTURED = new ConcurrentHashMap<>();

	private DragonMaceDeathMessage() {
	}

	/** How strongly a stack identifies itself as the Draconic Mace. */
	public static int rankOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return RANK_NONE;
		}

		if (DragonMaceIdentity.isDraconicMace(stack) || DragonMaceIdentity.isRevertedDraconicMace(stack)) {
			return RANK_DRACONIC;
		}

		return stack.has(DataComponents.CUSTOM_NAME) ? RANK_NAMED : RANK_NONE;
	}

	/** True when a snapshot of {@code rank} should replace one already stored as {@code existing}. */
	public static boolean shouldReplace(int existing, int rank) {
		return rank > existing;
	}

	/**
	 * True when the captured weapon may replace vanilla's wording.
	 *
	 * <p>Requires a Draconic snapshot and a damage source credited to the same player, which is what
	 * keeps unrelated and indirect kills on their normal messages.
	 */
	public static boolean shouldOverride(int rank, boolean creditedToCapturedKiller) {
		return rank >= RANK_DRACONIC && creditedToCapturedKiller;
	}

	/** The translation key vanilla uses when the killing weapon has a custom name. */
	public static String deathMessageKey(String msgId) {
		return "death.attack." + msgId + ".item";
	}

	/** Builds exactly the component vanilla builds for a named weapon. */
	public static Component buildMessage(String msgId, Component victim, Component killer, Component weapon) {
		return Component.translatable(deathMessageKey(msgId), victim, killer, weapon);
	}

	/**
	 * Snapshots the killer's main-hand weapon for {@code victim}.
	 *
	 * <p>Called both before and after the transfer, so a mace that is Draconic on either side of the
	 * transfer is remembered. A later snapshot only replaces an earlier one when it ranks higher, so a
	 * mace that is reverted during the transfer keeps its Draconic name.
	 */
	public static void capture(ServerPlayer victim, DamageSource source) {
		if (victim == null || source == null) {
			return;
		}

		ServerPlayer killer = DragonMaceDeathTransfer.resolveKiller(victim, source);

		if (killer == null || killer.getUUID().equals(victim.getUUID())) {
			return;
		}

		ItemStack stack = killer.getMainHandItem();
		int rank = rankOf(stack);

		if (rank < RANK_DRACONIC) {
			return;
		}

		CAPTURED.merge(victim.getUUID(), new Captured(killer.getUUID(), stack.getDisplayName(), rank),
				(existing, fresh) -> shouldReplace(existing.rank(), fresh.rank()) ? fresh : existing);
	}

	/**
	 * The weapon name to use for this death message, when an override applies.
	 *
	 * <p>Returns empty for every case the mod must not touch: no snapshot, a snapshot below Draconic,
	 * or a damage source not credited to the captured killer.
	 */
	public static Optional<Component> weaponFor(LivingEntity victim, DamageSource source) {
		if (victim == null || source == null) {
			return Optional.empty();
		}

		Captured captured = CAPTURED.get(victim.getUUID());

		if (captured == null) {
			return Optional.empty();
		}

		Entity causing = source.getEntity();
		boolean credited = causing instanceof ServerPlayer player
				&& player.getUUID().equals(captured.killer());

		if (!shouldOverride(captured.rank(), credited)) {
			return Optional.empty();
		}

		return Optional.of(captured.weapon());
	}

	/** Drops the snapshot for one victim. */
	public static void clear(UUID victim) {
		if (victim != null) {
			CAPTURED.remove(victim);
		}
	}

	/** Drops every snapshot; used by tests and on server shutdown. */
	public static void clearAll() {
		CAPTURED.clear();
	}
}
