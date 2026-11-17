package com.lorenz.dragonmace;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The global Draconic Mace enchantment configuration menu.
 *
 * <p>A fixed 27-slot display grid: Wind Burst 0–III on the left of the selection row, Breach IV and
 * Density V on the right, and Confirm / Info / Cancel in the action row. Every slot is display-only —
 * {@link #clicked} never delegates to the vanilla handler, so nothing can be taken, placed, swapped,
 * shift-clicked, dragged or number-key moved.
 *
 * <p>Selections live in {@link #pendingWindBurst} and {@link #pendingMain} and stay temporary until
 * Confirm is pressed, so closing the menu discards them. Confirm saves the configuration, reconciles
 * every online player's maces and starts the server-wide 120-second cooldown. The cooldown is read from
 * the saved configuration rather than from this menu, so it cannot be bypassed by reopening the menu,
 * by another player or by a restart.
 *
 * <p>Only the active Curse of the Dragons holder may use the menu. The check is repeated on every
 * click, so losing the curse mid-session closes the menu instead of leaving it usable.
 *
 * <p>There is no XP, material or currency cost.
 */
public class DragonMaceEnchantmentMenu extends AbstractContainerMenu {
	public static final int BUTTON_WIND_BURST_0 = 0;
	public static final int BUTTON_WIND_BURST_1 = 1;
	public static final int BUTTON_WIND_BURST_2 = 2;
	public static final int BUTTON_WIND_BURST_3 = 3;
	public static final int BUTTON_BREACH = 4;
	public static final int BUTTON_DENSITY = 5;
	public static final int BUTTON_APPLY = 6;
	public static final int BUTTON_CANCEL = 7;
	public static final int BUTTON_INFO = 8;

	public static final int DATA_WIND_BURST = 0;
	public static final int DATA_MAIN = 1;
	public static final int DATA_COOLDOWN = 2;

	private final Player player;
	private final SimpleContainer container = new SimpleContainer(DragonMaceEnchantmentLayout.SLOT_COUNT);
	private int pendingWindBurst;
	private int pendingMain;
	private int cooldownSeconds;

	public DragonMaceEnchantmentMenu(int containerId, Inventory inventory) {
		super(DragonMaceMenus.ENCHANTMENT, containerId);
		this.player = inventory.player;

		DragonMaceConfig config = DragonMaceConfig.get();
		this.pendingWindBurst = config.windBurstLevel();
		this.pendingMain = config.mainEnchantment().ordinal();
		this.cooldownSeconds = cooldownSeconds(config, System.currentTimeMillis());

		for (int slot = 0; slot < DragonMaceEnchantmentLayout.SLOT_COUNT; slot++) {
			addSlot(new DisplaySlot(this.container, slot,
					DragonMaceEnchantmentLayout.slotX(slot),
					DragonMaceEnchantmentLayout.slotY(slot)));
		}

		refresh();
	}

	/** The Wind Burst level currently selected but not yet applied. */
	public int pendingWindBurst() {
		return this.pendingWindBurst;
	}

	/** The main enchantment currently selected but not yet applied. */
	public DragonMaceConfig.MainEnchantment pendingMain() {
		return DragonMaceConfig.MainEnchantment.values()[this.pendingMain];
	}

	/** Whole seconds left on the server-wide cooldown, as last synced. */
	public int cooldownSeconds() {
		return this.cooldownSeconds;
	}

	/** True when Confirm is currently allowed. */
	public boolean canApply() {
		return this.cooldownSeconds <= 0;
	}

	/** The display container, exposed so tests can assert the layout. */
	public SimpleContainer displayContainer() {
		return this.container;
	}

	/** True when {@code player} may use this menu: they must currently own the curse. */
	public static boolean isAuthorized(Player player) {
		if (player == null) {
			return false;
		}

		MinecraftServer server = player.level().getServer();
		return server != null && DragonMaceCurse.isActive(server, player.getUUID());
	}

	/** The Wind Burst level a button selects, or {@code current} when the button is not a selection. */
	public static int windBurstAfter(int buttonId, int current) {
		if (buttonId >= BUTTON_WIND_BURST_0 && buttonId <= BUTTON_WIND_BURST_3) {
			return DragonMaceConfig.clampWindBurst(buttonId);
		}

		return current;
	}

	/** The main enchantment ordinal a button selects, or {@code current} when it is not a selection. */
	public static int mainAfter(int buttonId, int current) {
		if (buttonId == BUTTON_BREACH) {
			return DragonMaceConfig.MainEnchantment.BREACH.ordinal();
		}

		if (buttonId == BUTTON_DENSITY) {
			return DragonMaceConfig.MainEnchantment.DENSITY.ordinal();
		}

		return current;
	}

	private static int cooldownSeconds(DragonMaceConfig config, long now) {
		long remaining = config.remainingCooldownMillis(now);
		return (int) ((remaining + 999L) / 1000L);
	}

	/** Rewrites every display slot from the pending selection. */
	private void refresh() {
		RegistryAccess registries = this.player == null ? null : this.player.level().registryAccess();
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, this.pendingWindBurst,
				pendingMain());

		for (int slot = 0; slot < DragonMaceEnchantmentLayout.SLOT_COUNT; slot++) {
			this.container.setItem(slot, items[slot]);
		}
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
		// Never delegate: the vanilla handler is what would move items.
		if (!isAuthorized(clicker)) {
			return;
		}

		if (slotId < 0 || slotId >= DragonMaceEnchantmentLayout.SLOT_COUNT) {
			return;
		}

		if (clickType != ClickType.PICKUP && clickType != ClickType.PICKUP_ALL) {
			return;
		}

		clickMenuButton(clicker, buttonIdFor(slotId));
	}

	/** Maps a display slot to the button id it stands for, or -1 when it is inert. */
	static int buttonIdFor(int slot) {
		if (DragonMaceEnchantmentLayout.isWindBurstSlot(slot)) {
			return BUTTON_WIND_BURST_0 + DragonMaceEnchantmentLayout.windBurstLevelOf(slot);
		}

		return switch (slot) {
			case DragonMaceEnchantmentLayout.SLOT_BREACH -> BUTTON_BREACH;
			case DragonMaceEnchantmentLayout.SLOT_DENSITY -> BUTTON_DENSITY;
			case DragonMaceEnchantmentLayout.SLOT_CONFIRM -> BUTTON_APPLY;
			case DragonMaceEnchantmentLayout.SLOT_INFO -> BUTTON_INFO;
			case DragonMaceEnchantmentLayout.SLOT_CANCEL -> BUTTON_CANCEL;
			default -> -1;
		};
	}

	@Override
	public boolean clickMenuButton(Player clicker, int id) {
		if (!isAuthorized(clicker)) {
			return false;
		}

		if (id >= BUTTON_WIND_BURST_0 && id <= BUTTON_WIND_BURST_3) {
			this.pendingWindBurst = windBurstAfter(id, this.pendingWindBurst);
			refresh();
			broadcastChanges();
			return true;
		}

		if (id == BUTTON_BREACH || id == BUTTON_DENSITY) {
			this.pendingMain = mainAfter(id, this.pendingMain);
			refresh();
			broadcastChanges();
			return true;
		}

		if (id == BUTTON_APPLY) {
			return apply(clicker);
		}

		if (id == BUTTON_CANCEL) {
			if (clicker instanceof ServerPlayer serverPlayer) {
				serverPlayer.closeContainer();
			}

			return true;
		}

		// Info is hover-only: it never changes the configuration.
		return id == BUTTON_INFO;
	}

	/**
	 * Commits the pending selection.
	 *
	 * @return true when the configuration was saved and applied
	 */
	public boolean apply(Player clicker) {
		MinecraftServer server = clicker.level().getServer();

		if (server == null || !isAuthorized(clicker)) {
			return false;
		}

		DragonMaceConfig current = DragonMaceConfig.get();
		long now = System.currentTimeMillis();
		ApplyResult decision = decideApply(current, this.pendingWindBurst, this.pendingMain, now);

		if (decision == ApplyResult.COOLDOWN) {
			this.cooldownSeconds = cooldownSeconds(current, now);
			broadcastChanges();
			notify(clicker, "The enchantment cooldown is still active: "
					+ this.cooldownSeconds + "s remaining.");
			return false;
		}

		if (decision != ApplyResult.APPLIED) {
			notify(clicker, "That enchantment selection is not valid.");
			return false;
		}

		DragonMaceConfig next = current.withWindBurstLevel(this.pendingWindBurst)
				.withMainEnchantment(pendingMain())
				.withLastApply(now);

		if (!DragonMaceConfig.save(next)) {
			notify(clicker, "The enchantment configuration could not be saved.");
			return false;
		}

		DragonMaceEnchantments.reconcileAll(server, server.registryAccess(), next);
		this.cooldownSeconds = cooldownSeconds(next, now);
		refresh();
		broadcastChanges();

		notify(clicker, "Draconic Mace enchantments set to "
				+ DragonMaceEnchantmentLayout.windBurstName(next.windBurstLevel()) + " and "
				+ DragonMaceEnchantmentLayout.mainName(next.mainEnchantment()) + ".");

		if (clicker instanceof ServerPlayer serverPlayer) {
			serverPlayer.closeContainer();
		}

		DragonMace.LOGGER.info("Draconic Mace enchantments applied by {}: wind burst {}, {}.",
				clicker.getName().getString(), next.windBurstLevel(), next.mainEnchantment().id());
		return true;
	}

	private static void notify(Player player, String message) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal(message));
		}
	}

	/** The outcome of a Confirm press. */
	public enum ApplyResult {
		APPLIED,
		COOLDOWN,
		INVALID
	}

	/**
	 * Decides whether a Confirm press may commit.
	 *
	 * <p>Pure so the whole validation order can be tested without a menu or a player: the selection is
	 * validated first, then the server-wide cooldown is checked against the saved configuration.
	 */
	public static ApplyResult decideApply(DragonMaceConfig current, int windBurst, int mainOrdinal,
			long now) {
		if (current == null) {
			return ApplyResult.INVALID;
		}

		if (windBurst < 0 || windBurst > DragonMaceConfig.MAX_WIND_BURST_LEVEL) {
			return ApplyResult.INVALID;
		}

		if (mainOrdinal < 0 || mainOrdinal >= DragonMaceConfig.MainEnchantment.values().length) {
			return ApplyResult.INVALID;
		}

		return current.canApply(now) ? ApplyResult.APPLIED : ApplyResult.COOLDOWN;
	}

	@Override
	public void setData(int id, int value) {
		switch (id) {
			case DATA_WIND_BURST -> this.pendingWindBurst = DragonMaceConfig.clampWindBurst(value);
			case DATA_MAIN -> this.pendingMain = Math.max(0,
					Math.min(DragonMaceConfig.MainEnchantment.values().length - 1, value));
			case DATA_COOLDOWN -> this.cooldownSeconds = Math.max(0, value);
			default -> {
			}
		}
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		dragonmace$broadcastState();
	}

	/** Pushes the pending selection and the cooldown to the client. */
	private void dragonmace$broadcastState() {
		if (this.player instanceof ServerPlayer serverPlayer) {
			sendDataToRemote(serverPlayer, DATA_WIND_BURST, this.pendingWindBurst);
			sendDataToRemote(serverPlayer, DATA_MAIN, this.pendingMain);
			sendDataToRemote(serverPlayer, DATA_COOLDOWN, this.cooldownSeconds);
		}
	}

	private void sendDataToRemote(ServerPlayer serverPlayer, int id, int value) {
		serverPlayer.connection.send(new ClientboundContainerSetDataPacket(this.containerId, id, value));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return isAuthorized(player);
	}

	/** A slot that can never be taken from or placed into. */
	static final class DisplaySlot extends Slot {
		DisplaySlot(net.minecraft.world.Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void setByPlayer(ItemStack stack) {
			// Display-only: never let a client-driven write replace the layout.
		}

		@Override
		public void setByPlayer(ItemStack stack, ItemStack previous) {
		}
	}
}
