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
 * The global Draconic Mace enchantment and rune configuration menu.
 *
 * <p>A fixed 54-slot display grid. Rows 0–2 keep the original enchantment section: Wind Burst 0–III on
 * the left of the selection row, Breach IV and Density V on the right, and Confirm / Info / Cancel in the
 * action row. Rows 3–5 add one rune row per category in the order Kill, Aura, Attack: an empty slot, the
 * category info icon, five rune options and two apply slots. Every slot is display-only —
 * {@link #clicked} never delegates to the vanilla handler, so nothing can be taken, placed, swapped,
 * shift-clicked, dragged or number-key moved.
 *
 * <p>The two rightmost slots of each rune row apply that row's rune immediately, without the 120-second
 * cooldown, which stays reserved for the enchantment Confirm button.
 *
 * <p>Selections live in the {@code pending*} fields and stay temporary until Confirm is pressed, so
 * closing the menu discards them. Confirm saves the configuration and the rune selection together,
 * reconciles every online player's maces and starts the server-wide 120-second cooldown. The cooldown is
 * read from the saved configuration rather than from this menu, so it cannot be bypassed by reopening the
 * menu, by another player or by a restart.
 *
 * <p>Only the active Curse of the Dragons holder may use the menu. The check is repeated on every click,
 * so losing the curse mid-session closes the menu instead of leaving it usable. A rune that is not
 * unlocked at the player's kill count is refused server-side, so a client cannot fake an unlock.
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

	/** First rune button id; each rune occupies {@code base + category * 5 + rune}. */
	public static final int BUTTON_RUNE_BASE = 100;

	/** First rune-apply button id; each category occupies {@code base + category}. */
	public static final int BUTTON_RUNE_APPLY_BASE = 200;

	/** First rune-toggle button id; each category occupies {@code base + category}. */
	public static final int BUTTON_RUNE_TOGGLE_BASE = 300;

	public static final int DATA_WIND_BURST = 0;
	public static final int DATA_MAIN = 1;
	public static final int DATA_COOLDOWN = 2;
	public static final int DATA_KILL_RUNE = 3;
	public static final int DATA_ATTACK_RUNE = 4;
	public static final int DATA_AURA_RUNE = 5;
	public static final int DATA_KILLS = 6;

	private final Player player;
	private final SimpleContainer container = new SimpleContainer(DragonMaceEnchantmentLayout.SLOT_COUNT);
	private int pendingWindBurst;
	private int pendingMain;
	private int cooldownSeconds;
	private int kills;
	private DragonMaceRune pendingKillRune;
	private DragonMaceRune pendingAttackRune;
	private DragonMaceRune pendingAuraRune;
	private DragonMaceRuneToggles pendingToggles = DragonMaceRuneToggles.defaults();

	public DragonMaceEnchantmentMenu(int containerId, Inventory inventory) {
		super(DragonMaceMenus.ENCHANTMENT, containerId);
		this.player = inventory.player;

		DragonMaceConfig config = DragonMaceConfig.get();
		this.pendingWindBurst = config.windBurstLevel();
		this.pendingMain = config.mainEnchantment().ordinal();
		this.cooldownSeconds = cooldownSeconds(config, System.currentTimeMillis());

		MinecraftServer server = this.player == null ? null : this.player.level().getServer();
		this.kills = server == null || this.player == null
				? 0
				: DragonMaceProgression.kills(server, this.player.getUUID());
		DragonMaceRuneSelection saved = server == null || this.player == null
				? DragonMaceRuneSelection.defaults()
				: DragonMaceProgression.runes(server, this.player.getUUID());
		this.pendingKillRune = saved.kill();
		this.pendingAttackRune = saved.attack();
		this.pendingAuraRune = saved.aura();
		this.pendingToggles = server == null || this.player == null
				? DragonMaceRuneToggles.defaults()
				: DragonMaceProgression.toggles(server, this.player.getUUID());

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

	/** The rune selection currently selected but not yet applied. */
	public DragonMaceRuneSelection pendingRunes() {
		return new DragonMaceRuneSelection(this.pendingKillRune, this.pendingAttackRune,
				this.pendingAuraRune);
	}

	/** The player's kill count, which decides which runes are unlocked. */
	public int kills() {
		return this.kills;
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

	/** The rune button id of {@code rune} in {@code category}. */
	public static int runeButton(DragonMaceRuneCategory category, DragonMaceRune rune) {
		return BUTTON_RUNE_BASE + category.ordinal() * DragonMaceRune.values().length + rune.ordinal();
	}

	/** The category a rune button belongs to, or {@code null} when the id is not a rune button. */
	public static DragonMaceRuneCategory categoryForButton(int buttonId) {
		int index = buttonId - BUTTON_RUNE_BASE;
		int total = DragonMaceRuneCategory.values().length * DragonMaceRune.values().length;

		if (index < 0 || index >= total) {
			return null;
		}

		return DragonMaceRuneCategory.values()[index / DragonMaceRune.values().length];
	}

	/** The rune a rune button selects, or {@code null} when the id is not a rune button. */
	public static DragonMaceRune runeForButton(int buttonId) {
		DragonMaceRuneCategory category = categoryForButton(buttonId);

		if (category == null) {
			return null;
		}

		int index = buttonId - BUTTON_RUNE_BASE - category.ordinal() * DragonMaceRune.values().length;
		return DragonMaceRune.values()[index];
	}

	/**
	 * The rune a button selects, or {@code current} when the button is not a rune button or the rune is
	 * still locked at {@code kills}.
	 *
	 * <p>Pure, so the unlock gate can be tested without a menu: a locked rune never replaces the current
	 * selection, which is what stops a client from selecting an unearned rune.
	 */
	public static DragonMaceRune runeAfter(int buttonId, DragonMaceRune current, int kills) {
		DragonMaceRune rune = runeForButton(buttonId);

		if (rune == null || !rune.isUnlockedAt(kills)) {
			return current;
		}

		return rune;
	}

	private static int cooldownSeconds(DragonMaceConfig config, long now) {
		long remaining = config.remainingCooldownMillis(now);
		return (int) ((remaining + 999L) / 1000L);
	}

	/** Rewrites every display slot from the pending selection. */
	private void refresh() {
		RegistryAccess registries = this.player == null ? null : this.player.level().registryAccess();
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, this.pendingWindBurst,
				pendingMain(), pendingRunes(), this.kills, this.pendingToggles);

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

		DragonMaceRuneCategory category = DragonMaceEnchantmentLayout.categoryOf(slot);

		if (category != null) {
			DragonMaceRune rune = DragonMaceEnchantmentLayout.runeOf(slot);
			return rune == null ? -1 : runeButton(category, rune);
		}

		DragonMaceRuneCategory applyCategory = DragonMaceEnchantmentLayout.categoryOfApply(slot);

		if (applyCategory != null) {
			return runeApplyButton(applyCategory);
		}

		DragonMaceRuneCategory toggleCategory = DragonMaceEnchantmentLayout.categoryOfToggle(slot);

		if (toggleCategory != null) {
			return runeToggleButton(toggleCategory);
		}

		if (DragonMaceEnchantmentLayout.isRuneInfoSlot(slot)) {
			return BUTTON_INFO;
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

		DragonMaceRuneCategory category = categoryForButton(id);

		if (category != null) {
			DragonMaceRune rune = runeAfter(id, pendingRunes().get(category), this.kills);

			if (rune == pendingRunes().get(category)) {
				// Locked or unchanged: nothing to do, and no state is touched.
				return false;
			}

			switch (category) {
				case KILL -> this.pendingKillRune = rune;
				case ATTACK -> this.pendingAttackRune = rune;
				case AURA -> this.pendingAuraRune = rune;
			}

			refresh();
			broadcastChanges();
			return true;
		}

		DragonMaceRuneCategory applyCategory = runeApplyCategory(id);

		if (applyCategory != null) {
			return applyRunes(clicker, applyCategory);
		}

		DragonMaceRuneCategory toggleCategory = runeToggleCategory(id);

		if (toggleCategory != null) {
			return toggleRunes(clicker, toggleCategory);
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

	/** The rune-apply button id of {@code category}. */
	public static int runeApplyButton(DragonMaceRuneCategory category) {
		return BUTTON_RUNE_APPLY_BASE + category.ordinal();
	}

	/** The category a rune-apply button belongs to, or {@code null} when the id is not one. */
	public static DragonMaceRuneCategory runeApplyCategory(int buttonId) {
		int index = buttonId - BUTTON_RUNE_APPLY_BASE;

		if (index < 0 || index >= DragonMaceRuneCategory.values().length) {
			return null;
		}

		return DragonMaceRuneCategory.values()[index];
	}

	/** The rune-toggle button id of {@code category}. */
	public static int runeToggleButton(DragonMaceRuneCategory category) {
		return BUTTON_RUNE_TOGGLE_BASE + category.ordinal();
	}

	/** The category a rune-toggle button belongs to, or {@code null} when the id is not one. */
	public static DragonMaceRuneCategory runeToggleCategory(int buttonId) {
		int index = buttonId - BUTTON_RUNE_TOGGLE_BASE;

		if (index < 0 || index >= DragonMaceRuneCategory.values().length) {
			return null;
		}

		return DragonMaceRuneCategory.values()[index];
	}

	/**
	 * Flips one category's toggle.
	 *
	 * <p>Independent of the rune selection, of the other two categories and of the enchantment cooldown:
	 * no configuration is read or written and no timestamp is stamped. The selected rune is untouched, so
	 * turning a category back on restores exactly the rune the player had.
	 *
	 * @return true when the stored value changed
	 */
	public boolean toggleRunes(Player clicker, DragonMaceRuneCategory category) {
		MinecraftServer server = clicker.level().getServer();

		if (server == null || category == null || !isAuthorized(clicker)) {
			return false;
		}

		DragonMaceRuneToggles next = this.pendingToggles.toggled(category);

		if (!DragonMaceProgression.saveToggles(server, clicker.getUUID(), next)) {
			return false;
		}

		this.pendingToggles = next;
		refresh();
		broadcastChanges();
		notify(clicker, category.label() + (next.get(category) ? " enabled." : " disabled."));
		return true;
	}

	/**
	 * True when {@code selection} may be applied as runes.
	 *
	 * <p>Rune application is deliberately independent of the enchantment cooldown: it takes no
	 * configuration and no timestamp, so the 120-second cooldown can never block it. Only the unlock
	 * gate applies.
	 */
	public static boolean canApplyRunes(DragonMaceRuneSelection selection, int kills) {
		return DragonMaceProgression.isSelectionLegal(selection, kills);
	}

	/**
	 * The selection stored when {@code category} is applied.
	 *
	 * <p>Only that one category is taken from the pending selection, so applying a row never commits a
	 * rune the player picked in another row but has not applied yet.
	 */
	public static DragonMaceRuneSelection runeSelectionAfterApply(DragonMaceRuneSelection saved,
			DragonMaceRuneSelection pending, DragonMaceRuneCategory category) {
		DragonMaceRuneSelection base = DragonMaceRuneSelection.orDefaults(saved);

		if (category == null) {
			return base;
		}

		return base.with(category, DragonMaceRuneSelection.orDefaults(pending).get(category));
	}

	/**
	 * Applies one rune row's selection to the mace.
	 *
	 * <p>No cooldown is read or written: the rune is stored through
	 * {@link DragonMaceProgression#saveRunes}, which re-lores the holder's maces immediately, and the
	 * menu stays open so the other rows can be applied in the same visit.
	 *
	 * @return true when the selection was stored
	 */
	public boolean applyRunes(Player clicker, DragonMaceRuneCategory category) {
		MinecraftServer server = clicker.level().getServer();

		if (server == null || category == null || !isAuthorized(clicker)) {
			return false;
		}

		DragonMaceRuneSelection next = runeSelectionAfterApply(
				DragonMaceProgression.runes(server, clicker.getUUID()), pendingRunes(), category);

		if (!canApplyRunes(next, this.kills)) {
			notify(clicker, "That rune is not unlocked yet.");
			return false;
		}

		if (!DragonMaceProgression.saveRunes(server, clicker.getUUID(), next)) {
			notify(clicker, "The rune selection could not be saved.");
			return false;
		}

		switch (category) {
			case KILL -> this.pendingKillRune = next.kill();
			case ATTACK -> this.pendingAttackRune = next.attack();
			case AURA -> this.pendingAuraRune = next.aura();
		}

		refresh();
		broadcastChanges();
		notify(clicker, category.label() + " set to " + next.get(category).displayName() + ".");
		return true;
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
		ApplyResult decision = decideApply(current, this.pendingWindBurst, this.pendingMain,
				pendingRunes(), this.kills, now);

		if (decision == ApplyResult.COOLDOWN) {
			this.cooldownSeconds = cooldownSeconds(current, now);
			broadcastChanges();
			notify(clicker, "The enchantment cooldown is still active: "
					+ this.cooldownSeconds + "s remaining.");
			return false;
		}

		if (decision != ApplyResult.APPLIED) {
			notify(clicker, "That selection is not valid.");
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
		DragonMaceProgression.saveRunes(server, clicker.getUUID(), pendingRunes());
		this.cooldownSeconds = cooldownSeconds(next, now);
		refresh();
		broadcastChanges();

		notify(clicker, "Draconic Mace set to "
				+ DragonMaceEnchantmentLayout.windBurstName(next.windBurstLevel()) + ", "
				+ DragonMaceEnchantmentLayout.mainName(next.mainEnchantment()) + " and runes "
				+ DragonMaceEnchantmentLayout.runeSummary(pendingRunes()) + ".");

		if (clicker instanceof ServerPlayer serverPlayer) {
			serverPlayer.closeContainer();
		}

		DragonMace.LOGGER.info("Draconic Mace configuration applied by {}: wind burst {}, {}, runes {}.",
				clicker.getName().getString(), next.windBurstLevel(), next.mainEnchantment().id(),
				DragonMaceEnchantmentLayout.runeSummary(pendingRunes()));
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
		return decideApply(current, windBurst, mainOrdinal, DragonMaceRuneSelection.defaults(), 0, now);
	}

	/**
	 * Decides whether a Confirm press may commit, including the rune selection.
	 *
	 * <p>Validation order: the enchantment selection, then the rune selection against the player's own
	 * kill count, then the server-wide cooldown.
	 */
	public static ApplyResult decideApply(DragonMaceConfig current, int windBurst, int mainOrdinal,
			DragonMaceRuneSelection runes, int kills, long now) {
		if (current == null) {
			return ApplyResult.INVALID;
		}

		if (windBurst < 0 || windBurst > DragonMaceConfig.MAX_WIND_BURST_LEVEL) {
			return ApplyResult.INVALID;
		}

		if (mainOrdinal < 0 || mainOrdinal >= DragonMaceConfig.MainEnchantment.values().length) {
			return ApplyResult.INVALID;
		}

		if (!DragonMaceProgression.isSelectionLegal(runes, kills)) {
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
			case DATA_KILL_RUNE -> this.pendingKillRune = runeFromData(value, this.pendingKillRune);
			case DATA_ATTACK_RUNE -> this.pendingAttackRune = runeFromData(value, this.pendingAttackRune);
			case DATA_AURA_RUNE -> this.pendingAuraRune = runeFromData(value, this.pendingAuraRune);
			case DATA_KILLS -> this.kills = Math.max(0, value);
			default -> {
			}
		}
	}

	private static DragonMaceRune runeFromData(int ordinal, DragonMaceRune current) {
		DragonMaceRune[] runes = DragonMaceRune.values();
		return ordinal >= 0 && ordinal < runes.length ? runes[ordinal] : current;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		dragonmace$broadcastState();
	}

	/** Pushes the pending selection, the rune selection and the cooldown to the client. */
	private void dragonmace$broadcastState() {
		if (this.player instanceof ServerPlayer serverPlayer) {
			sendDataToRemote(serverPlayer, DATA_WIND_BURST, this.pendingWindBurst);
			sendDataToRemote(serverPlayer, DATA_MAIN, this.pendingMain);
			sendDataToRemote(serverPlayer, DATA_COOLDOWN, this.cooldownSeconds);
			sendDataToRemote(serverPlayer, DATA_KILL_RUNE, this.pendingKillRune.ordinal());
			sendDataToRemote(serverPlayer, DATA_ATTACK_RUNE, this.pendingAttackRune.ordinal());
			sendDataToRemote(serverPlayer, DATA_AURA_RUNE, this.pendingAuraRune.ordinal());
			sendDataToRemote(serverPlayer, DATA_KILLS, this.kills);
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
