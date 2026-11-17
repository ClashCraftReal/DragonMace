package com.lorenz.dragonmace;

import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Curse-based enchantment ownership.
 *
 * <p>Every mace keeps enchantments only while it sits in the inventory of the player who currently
 * owns the Curse of the Dragons. Anywhere else — another player's inventory, a chest, a shelf, a
 * bundle, a hopper, the ground — every mace is stripped back to an unenchanted mace. Only maces are
 * touched, so swords, bows, armour, tools and every unrelated item keep their enchantments.
 *
 * <p>The rule is derived from where the stack is, never from a stored flag, so it cannot drift: the
 * same {@link #reconcile} call is used by the inventory hooks, the container hooks, the curse transfer
 * and the configuration apply. Every method is idempotent, so repeated reconciliation is free.
 */
public final class DragonMaceEnchantments {
	private DragonMaceEnchantments() {
	}

	/** True when the stack is a mace, the only item type this rule governs. */
	public static boolean isMace(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(Items.MACE);
	}

	/** True when {@code player} currently owns the curse and may therefore keep enchanted maces. */
	public static boolean isEligible(MinecraftServer server, UUID player) {
		return DragonMaceCurse.isActive(server, player);
	}

	/** The enchantments the global configuration allows, as a component. */
	public static ItemEnchantments configured(RegistryAccess registries, DragonMaceConfig config) {
		if (registries == null) {
			return ItemEnchantments.EMPTY;
		}

		DragonMaceConfig settings = config == null ? DragonMaceConfig.defaults() : config;
		HolderLookup.RegistryLookup<Enchantment> registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		Holder.Reference<Enchantment> main = settings.mainEnchantment() == DragonMaceConfig.MainEnchantment.BREACH
				? registry.getOrThrow(Enchantments.BREACH)
				: registry.getOrThrow(Enchantments.DENSITY);
		enchantments.set(main, settings.mainEnchantment().level());

		if (settings.windBurstLevel() > 0) {
			enchantments.set(registry.getOrThrow(Enchantments.WIND_BURST), settings.windBurstLevel());
		}

		return enchantments.toImmutable();
	}

	/** True when the stack already carries exactly {@code expected} and nothing else. */
	public static boolean matches(ItemStack stack, ItemEnchantments expected) {
		return stack.getEnchantments().equals(expected);
	}

	/**
	 * Applies the configured enchantments to one stack, replacing whatever it had.
	 *
	 * @return true when the stack changed
	 */
	public static boolean applyConfigured(ItemStack stack, RegistryAccess registries,
			DragonMaceConfig config) {
		if (!isMace(stack)) {
			return false;
		}

		ItemEnchantments expected = configured(registries, config);

		if (matches(stack, expected)) {
			return false;
		}

		stack.set(DataComponents.ENCHANTMENTS, expected);
		return true;
	}

	/**
	 * Removes every enchantment from one stack.
	 *
	 * @return true when the stack changed
	 */
	public static boolean strip(ItemStack stack) {
		if (!isMace(stack)) {
			return false;
		}

		boolean changed = false;

		if (stack.has(DataComponents.ENCHANTMENTS)) {
			stack.remove(DataComponents.ENCHANTMENTS);
			changed = true;
		}

		if (stack.has(DataComponents.STORED_ENCHANTMENTS)) {
			stack.remove(DataComponents.STORED_ENCHANTMENTS);
			changed = true;
		}

		return changed;
	}

	/**
	 * Brings one stack in line with the ownership rule.
	 *
	 * @param eligible whether the stack currently sits in the curse holder's inventory
	 * @return true when the stack changed
	 */
	public static boolean reconcile(ItemStack stack, boolean eligible, RegistryAccess registries,
			DragonMaceConfig config) {
		if (!isMace(stack)) {
			return false;
		}

		return eligible ? applyConfigured(stack, registries, config) : strip(stack);
	}

	/**
	 * Reconciles every mace in {@code container}, in place.
	 *
	 * <p>Each stack is written back at its own slot, so nothing is moved, duplicated or deleted.
	 *
	 * @return the number of stacks changed
	 */
	public static int reconcileContainer(Container container, boolean eligible, RegistryAccess registries,
			DragonMaceConfig config) {
		if (container == null) {
			return 0;
		}

		int changed = 0;

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);

			if (reconcile(stack, eligible, registries, config)) {
				container.setItem(slot, stack);
				changed++;
			}
		}

		return changed;
	}

	/**
	 * Reconciles a player's whole inventory: main inventory, hotbar, armour slots and offhand.
	 *
	 * @return the number of stacks changed
	 */
	public static int reconcilePlayer(ServerPlayer player, RegistryAccess registries,
			DragonMaceConfig config) {
		if (player == null) {
			return 0;
		}

		boolean eligible = isEligible(player.level().getServer(), player.getUUID());
		return reconcileContainer(player.getInventory(), eligible, registries, config);
	}

	/**
	 * Reconciles every online player's inventory.
	 *
	 * <p>Used after a curse transfer or a global removal, so both the previous and the new holder are
	 * brought in line in one pass.
	 *
	 * @return the number of stacks changed
	 */
	public static int reconcileAll(MinecraftServer server, RegistryAccess registries,
			DragonMaceConfig config) {
		if (server == null) {
			return 0;
		}

		int changed = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			changed += reconcilePlayer(player, registries, config);
		}

		return changed;
	}
}
