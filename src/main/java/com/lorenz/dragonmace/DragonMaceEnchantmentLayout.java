package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The fixed 54-slot layout of the Draconic Mace enchantment and rune menu.
 *
 * <p>Rows 0–2 keep the original enchantment section unchanged: slots 0–8 are the empty top row, 9–17 the
 * selection row and 18–26 the action row. Rows 3–5 add the rune section, one row per category in the order
 * Kill, Aura, Attack. Each rune row has the same shape: an empty slot, the category's info icon, the five
 * rune options and two empty slots that apply the row's rune.
 *
 * <ul>
 * <li>Kill — 27 empty, 28 info, 29–33 the five runes, 34–35 apply.
 * <li>Aura — 36 empty, 37 info, 38–42 the five runes, 43–44 apply.
 * <li>Attack — 45 empty, 46 info, 47–51 the five runes, 52–53 apply.
 * </ul>
 *
 * <p>The info icon therefore always sits exactly two slots before the first rune option, and the two
 * rightmost slots of every rune row are the apply slots. The apply slots carry no display item, so the
 * layout stays empty there while a click still applies that row's rune.
 *
 * <p>The layout is a plain static table so it can be asserted by tests without constructing a menu or a
 * player. Every stack is display-only: the menu refuses all slot interaction, so nothing here can be
 * taken, placed, swapped or shift-clicked.
 */
public final class DragonMaceEnchantmentLayout {
	public static final int SLOT_COUNT = 54;

	public static final int SLOT_WIND_BURST_0 = 9;
	public static final int SLOT_WIND_BURST_1 = 10;
	public static final int SLOT_WIND_BURST_2 = 11;
	public static final int SLOT_WIND_BURST_3 = 12;
	public static final int SLOT_BREACH = 15;
	public static final int SLOT_DENSITY = 16;
	public static final int SLOT_CONFIRM = 21;
	public static final int SLOT_INFO = 22;
	public static final int SLOT_CANCEL = 23;

	/** First slot of each rune row, in row order: Kill, Aura, Attack. */
	public static final int SLOT_RUNE_KILL = 27;
	public static final int SLOT_RUNE_AURA = 36;
	public static final int SLOT_RUNE_ATTACK = 45;

	/** The category info icon sits one slot into the row, two slots before the first rune option. */
	public static final int RUNE_INFO_OFFSET = 1;

	/** The five rune options start two slots into the row. */
	public static final int RUNE_OFFSET = 2;

	/** The two rightmost slots of a rune row: the toggle, then the apply slot. */
	public static final int RUNE_TOGGLE_OFFSET = 7;
	public static final int RUNE_APPLY_OFFSET = 8;

	/** First slot of the selection row, in slot order. */
	private static final int[] WIND_BURST_SLOTS = {
			SLOT_WIND_BURST_0, SLOT_WIND_BURST_1, SLOT_WIND_BURST_2, SLOT_WIND_BURST_3
	};

	private DragonMaceEnchantmentLayout() {
	}

	/** The first slot of {@code category}'s rune row. */
	public static int runeRowStart(DragonMaceRuneCategory category) {
		return switch (category) {
			case KILL -> SLOT_RUNE_KILL;
			case AURA -> SLOT_RUNE_AURA;
			case ATTACK -> SLOT_RUNE_ATTACK;
		};
	}

	/** The slot that selects {@code rune} in {@code category}. */
	public static int slotForRune(DragonMaceRuneCategory category, DragonMaceRune rune) {
		return runeRowStart(category) + RUNE_OFFSET + (rune == null ? 0 : rune.ordinal());
	}

	/** The slot holding {@code category}'s info icon. */
	public static int runeInfoSlot(DragonMaceRuneCategory category) {
		return runeRowStart(category) + RUNE_INFO_OFFSET;
	}

	/** The slot that toggles {@code category} on or off, exactly one before its apply slot. */
	public static int runeToggleSlot(DragonMaceRuneCategory category) {
		return runeRowStart(category) + RUNE_TOGGLE_OFFSET;
	}

	/** The slot that applies {@code category}'s rune, the rightmost slot of the row. */
	public static int runeApplySlot(DragonMaceRuneCategory category) {
		return runeRowStart(category) + RUNE_APPLY_OFFSET;
	}

	/** True when the slot belongs to a rune row. */
	public static boolean isRuneSlot(int slot) {
		return categoryOf(slot) != null;
	}

	/** The category a rune slot belongs to, or {@code null} when the slot is not a rune slot. */
	public static DragonMaceRuneCategory categoryOf(int slot) {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			int start = runeRowStart(category) + RUNE_OFFSET;

			if (slot >= start && slot < start + DragonMaceRune.values().length) {
				return category;
			}
		}

		return null;
	}

	/** True when the slot holds a rune category's info icon. */
	public static boolean isRuneInfoSlot(int slot) {
		return categoryOfInfo(slot) != null;
	}

	/** The category an info slot belongs to, or {@code null} when the slot is not an info slot. */
	public static DragonMaceRuneCategory categoryOfInfo(int slot) {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			if (slot == runeInfoSlot(category)) {
				return category;
			}
		}

		return null;
	}

	/** True when the slot applies a rune category's selection. */
	public static boolean isRuneApplySlot(int slot) {
		return categoryOfApply(slot) != null;
	}

	/** The category an apply slot belongs to, or {@code null} when the slot is not an apply slot. */
	public static DragonMaceRuneCategory categoryOfApply(int slot) {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			if (slot == runeApplySlot(category)) {
				return category;
			}
		}

		return null;
	}

	/** True when the slot toggles a rune category on or off. */
	public static boolean isRuneToggleSlot(int slot) {
		return categoryOfToggle(slot) != null;
	}

	/** The category a toggle slot belongs to, or {@code null} when the slot is not a toggle slot. */
	public static DragonMaceRuneCategory categoryOfToggle(int slot) {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			if (slot == runeToggleSlot(category)) {
				return category;
			}
		}

		return null;
	}

	/** The rune a rune slot selects, or {@code null} when the slot is not a rune slot. */
	public static DragonMaceRune runeOf(int slot) {
		DragonMaceRuneCategory category = categoryOf(slot);

		if (category == null) {
			return null;
		}

		int index = slot - runeRowStart(category) - RUNE_OFFSET;
		DragonMaceRune[] runes = DragonMaceRune.values();
		return index >= 0 && index < runes.length ? runes[index] : null;
	}

	/** True when the slot selects a Wind Burst level. */
	public static boolean isWindBurstSlot(int slot) {
		return slot >= SLOT_WIND_BURST_0 && slot <= SLOT_WIND_BURST_3;
	}

	/** The Wind Burst level a slot selects, or -1 when the slot is not a Wind Burst slot. */
	public static int windBurstLevelOf(int slot) {
		return isWindBurstSlot(slot) ? slot - SLOT_WIND_BURST_0 : -1;
	}

	/** The slot that selects {@code level}, or -1 when the level is out of range. */
	public static int slotForWindBurst(int level) {
		return level >= 0 && level < WIND_BURST_SLOTS.length ? WIND_BURST_SLOTS[level] : -1;
	}

	/** The slot that selects {@code enchantment}. */
	public static int slotForMain(DragonMaceConfig.MainEnchantment enchantment) {
		return enchantment == DragonMaceConfig.MainEnchantment.BREACH ? SLOT_BREACH : SLOT_DENSITY;
	}

	/** Screen x of a slot inside the menu, relative to the menu's left edge. */
	public static int slotX(int slot) {
		return 8 + (slot % 9) * 18;
	}

	/** Screen y of a slot inside the menu, relative to the menu's top edge. */
	public static int slotY(int slot) {
		return 18 + (slot / 9) * 18;
	}

	/**
	 * Builds the 54 display stacks for one selection.
	 *
	 * @param registries used for the enchanted-book components; may be null in a bare test
	 * @param windBurst  the selected Wind Burst level, 0–3
	 * @param main       the selected main enchantment
	 */
	public static ItemStack[] build(RegistryAccess registries, int windBurst,
			DragonMaceConfig.MainEnchantment main) {
		return build(registries, windBurst, main, DragonMaceRuneSelection.defaults(), 0);
	}

	/**
	 * Builds the 54 display stacks for one selection, including the rune rows.
	 *
	 * @param registries used for the enchanted-book components; may be null in a bare test
	 * @param windBurst  the selected Wind Burst level, 0–3
	 * @param main       the selected main enchantment
	 * @param runes      the pending rune selection
	 * @param kills      the player's kill count, which decides which runes are unlocked
	 */
	public static ItemStack[] build(RegistryAccess registries, int windBurst,
			DragonMaceConfig.MainEnchantment main, DragonMaceRuneSelection runes, int kills) {
		return build(registries, windBurst, main, runes, kills, DragonMaceRuneToggles.defaults());
	}

	/**
	 * Builds the 54 display stacks for one selection, including the rune rows and their toggles.
	 *
	 * @param registries used for the enchanted-book components; may be null in a bare test
	 * @param windBurst  the selected Wind Burst level, 0–3
	 * @param main       the selected main enchantment
	 * @param runes      the pending rune selection
	 * @param kills      the player's kill count, which decides which runes are unlocked
	 * @param toggles    the pending on/off state of each rune category
	 */
	public static ItemStack[] build(RegistryAccess registries, int windBurst,
			DragonMaceConfig.MainEnchantment main, DragonMaceRuneSelection runes, int kills,
			DragonMaceRuneToggles toggles) {
		ItemStack[] items = new ItemStack[SLOT_COUNT];

		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			items[slot] = ItemStack.EMPTY;
		}

		int level = DragonMaceConfig.clampWindBurst(windBurst);
		DragonMaceConfig.MainEnchantment selectedMain = main == null
				? DragonMaceConfig.DEFAULT_MAIN_ENCHANTMENT
				: main;
		DragonMaceRuneSelection selectedRunes = DragonMaceRuneSelection.orDefaults(runes);
		DragonMaceRuneToggles selectedToggles = DragonMaceRuneToggles.orDefaults(toggles);

		items[SLOT_WIND_BURST_0] = windBurstOption(0, level == 0);
		items[SLOT_WIND_BURST_1] = windBurstOption(1, level == 1);
		items[SLOT_WIND_BURST_2] = windBurstOption(2, level == 2);
		items[SLOT_WIND_BURST_3] = windBurstOption(3, level == 3);

		items[SLOT_BREACH] = mainOption(registries, DragonMaceConfig.MainEnchantment.BREACH,
				selectedMain == DragonMaceConfig.MainEnchantment.BREACH);
		items[SLOT_DENSITY] = mainOption(registries, DragonMaceConfig.MainEnchantment.DENSITY,
				selectedMain == DragonMaceConfig.MainEnchantment.DENSITY);

		items[SLOT_CONFIRM] = confirmOption(level, selectedMain, selectedRunes);
		items[SLOT_INFO] = infoOption();
		items[SLOT_CANCEL] = cancelOption();

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			items[runeInfoSlot(category)] = runeInfoOption(category);
			items[runeToggleSlot(category)] = runeToggleOption(category, selectedToggles.get(category));

			for (DragonMaceRune rune : DragonMaceRune.values()) {
				items[slotForRune(category, rune)] = runeOption(category, rune,
						selectedRunes.get(category) == rune, rune.isUnlockedAt(kills));
			}
		}

		return items;
	}

	private static ItemStack windBurstOption(int level, boolean selected) {
		ItemStack stack = level == 0
				? new ItemStack(Items.BARRIER)
				: new ItemStack(Items.WIND_CHARGE, level);

		stack.set(DataComponents.CUSTOM_NAME, name(windBurstName(level)));

		List<Component> lore = new ArrayList<>();

		if (level == 0) {
			lore.add(line("Wind Burst is disabled.", ChatFormatting.GRAY));
			lore.add(line("No Wind Burst effect will be applied.", ChatFormatting.GRAY));
		} else {
			lore.add(line("Wind Burst level " + roman(level) + ".", ChatFormatting.GRAY));
			lore.add(line(level == 1
					? "Grants the Wind Burst mace ability."
					: level == 2
							? "Grants a stronger Wind Burst effect."
							: "Grants the strongest available Wind Burst effect.",
					ChatFormatting.GRAY));
		}

		return decorate(stack, lore, selected);
	}

	private static ItemStack mainOption(RegistryAccess registries,
			DragonMaceConfig.MainEnchantment enchantment, boolean selected) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		stack.set(DataComponents.CUSTOM_NAME, name(mainName(enchantment)));
		stack.set(DataComponents.STORED_ENCHANTMENTS, stored(registries, enchantment));

		List<Component> lore = new ArrayList<>();
		lore.add(line(enchantment == DragonMaceConfig.MainEnchantment.BREACH
				? "Breach reduces the effectiveness of the target's armor."
				: "Density increases smash attack damage.", ChatFormatting.GRAY));
		lore.add(line("Level " + roman(enchantment.level()) + ".", ChatFormatting.GRAY));

		return decorate(stack, lore, selected);
	}

	private static ItemStack confirmOption(int windBurst, DragonMaceConfig.MainEnchantment main,
			DragonMaceRuneSelection runes) {
		ItemStack stack = new ItemStack(Items.LIME_DYE);
		stack.set(DataComponents.CUSTOM_NAME, name("Confirm"));

		List<Component> lore = new ArrayList<>();
		lore.add(line("Apply Wind Burst " + roman(windBurst) + " and " + mainName(main) + ".",
				ChatFormatting.GRAY));
		lore.add(line("Runes: " + runeSummary(runes) + ".", ChatFormatting.GRAY));
		lore.add(line("Applies to every mace the curse holder carries.", ChatFormatting.GRAY));
		lore.add(line("Starts a 120 second server-wide cooldown.", ChatFormatting.GRAY));

		return withLore(stack, lore);
	}

	/** The three selected rune names, e.g. {@code Draconic / Soulflame / Warden}. */
	public static String runeSummary(DragonMaceRuneSelection runes) {
		DragonMaceRuneSelection value = DragonMaceRuneSelection.orDefaults(runes);
		return value.kill().displayName() + " / " + value.attack().displayName() + " / "
				+ value.aura().displayName();
	}

	/**
	 * One rune option.
	 *
	 * <p>The name always carries the rune's own colour, whether or not it is unlocked, so the colour is a
	 * property of the rune rather than of its state. The state is shown on its own line: green
	 * {@code Selected}, grey {@code Click to select}, or red {@code Locked} with the kill requirement.
	 */
	private static ItemStack runeOption(DragonMaceRuneCategory category, DragonMaceRune rune,
			boolean selected, boolean unlocked) {
		ItemStack stack = new ItemStack(runeItem(rune));
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(rune.displayName()).withStyle(rune.colour()));

		List<Component> lore = new ArrayList<>();
		lore.add(line(category.label(), ChatFormatting.DARK_GRAY));
		lore.add(line(categoryDescription(category), ChatFormatting.GRAY));

		if (unlocked) {
			lore.add(line("Unlocked", ChatFormatting.GREEN));
		} else {
			lore.add(line("Locked", ChatFormatting.RED));
			lore.add(line("Requires " + rune.unlockKills() + " kills.", ChatFormatting.GRAY));
		}

		lore.add(Component.empty());

		if (selected) {
			lore.add(line("Selected", ChatFormatting.GREEN));
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		} else if (unlocked) {
			lore.add(line("Click to select", ChatFormatting.DARK_GRAY));
		} else {
			lore.add(line("Not available yet", ChatFormatting.DARK_GRAY));
		}

		stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
		return stack;
	}

	/** The one-line description of a rune category, shared by its info icon and its rune options. */
	public static String categoryDescription(DragonMaceRuneCategory category) {
		return switch (category) {
			case KILL -> "Bursts rune particles on a defeated player.";
			case ATTACK -> "Applies the rune effect to the entity you strike.";
			case AURA -> "Trails rune particles around you.";
		};
	}

	/**
	 * One category info icon.
	 *
	 * <p>Hover-only: it repeats the category description and explains how the row is used, and never
	 * changes the selection. The icon sits exactly two slots before the first rune option.
	 */
	private static ItemStack runeInfoOption(DragonMaceRuneCategory category) {
		ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
		stack.set(DataComponents.CUSTOM_NAME, name(category.label() + " Info"));

		List<Component> lore = new ArrayList<>();
		lore.add(line(categoryDescription(category), ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(line("Five runes, one per category.", ChatFormatting.GRAY));
		lore.add(line("Runes unlock permanently as you land kills.", ChatFormatting.GRAY));
		lore.add(line("Click a rune to select it.", ChatFormatting.GRAY));
		lore.add(line("Click the rightmost slots to apply it.", ChatFormatting.GRAY));
		lore.add(line("Applying a rune has no cooldown.", ChatFormatting.GRAY));

		return withLore(stack, lore);
	}

	/**
	 * One category's on/off switch.
	 *
	 * <p>A lime dye when the category is enabled and a gray dye when it is disabled, so the state is
	 * readable at a glance as well as in the tooltip. Clicking it flips only this category and never
	 * touches the selected rune or the cooldown.
	 */
	private static ItemStack runeToggleOption(DragonMaceRuneCategory category, boolean enabled) {
		ItemStack stack = new ItemStack(enabled ? Items.LIME_DYE : Items.GRAY_DYE);
		stack.set(DataComponents.CUSTOM_NAME, name(category.label() + ": "
				+ (enabled ? "Enabled" : "Disabled")));

		List<Component> lore = new ArrayList<>();
		lore.add(line(enabled ? "Enabled" : "Disabled",
				enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
		lore.add(line(categoryDescription(category), ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(line(enabled
				? "Click to disable this rune category."
				: "Click to enable this rune category.", ChatFormatting.GRAY));
		lore.add(line("The selected rune is kept either way.", ChatFormatting.DARK_GRAY));
		lore.add(line("Toggling has no cooldown.", ChatFormatting.DARK_GRAY));

		return withLore(stack, lore);
	}

	/** The display item of a rune. */
	public static net.minecraft.world.item.Item runeItem(DragonMaceRune rune) {
		return switch (DragonMaceRune.byIdOrDefault(rune == null ? null : rune.id())) {
			case DRACONIC -> Items.DRAGON_BREATH;
			case SOULFLAME -> Items.SOUL_TORCH;
			case TEMPEST -> Items.WIND_CHARGE;
			case ABYSSAL -> Items.ENDER_EYE;
			case WARDEN -> Items.ECHO_SHARD;
		};
	}

	private static ItemStack infoOption() {
		ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
		stack.set(DataComponents.CUSTOM_NAME, name("Info"));

		List<Component> lore = new ArrayList<>();
		lore.add(line("Maces keep enchantments only while they are", ChatFormatting.GRAY));
		lore.add(line("inside the inventory of the curse holder.", ChatFormatting.GRAY));
		lore.add(line("Pick a Wind Burst level and one main enchantment.", ChatFormatting.GRAY));
		lore.add(line("Breach IV and Density V are mutually exclusive.", ChatFormatting.GRAY));
		lore.add(line("Nothing changes until Confirm is pressed.", ChatFormatting.GRAY));
		lore.add(line("Confirm starts a 120 second global cooldown.", ChatFormatting.GRAY));
		lore.add(line("Runes unlock permanently as you land kills.", ChatFormatting.GRAY));
		lore.add(line("Each category holds one rune of its own.", ChatFormatting.GRAY));

		return withLore(stack, lore);
	}

	private static ItemStack cancelOption() {
		ItemStack stack = new ItemStack(Items.RED_DYE);
		stack.set(DataComponents.CUSTOM_NAME, name("Cancel"));

		List<Component> lore = new ArrayList<>();
		lore.add(line("Closes the menu and discards the selection.", ChatFormatting.GRAY));
		lore.add(line("The global configuration is not changed.", ChatFormatting.GRAY));

		return withLore(stack, lore);
	}

	private static ItemStack withLore(ItemStack stack, List<Component> lore) {
		stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
		return stack;
	}

	private static ItemStack decorate(ItemStack stack, List<Component> lore, boolean selected) {
		lore.add(Component.empty());

		if (selected) {
			lore.add(line("Selected", ChatFormatting.GREEN));
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		} else {
			lore.add(line("Click to select", ChatFormatting.DARK_GRAY));
		}

		stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
		return stack;
	}

	private static ItemEnchantments stored(RegistryAccess registries,
			DragonMaceConfig.MainEnchantment enchantment) {
		if (registries == null) {
			return ItemEnchantments.EMPTY;
		}

		Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(enchantment == DragonMaceConfig.MainEnchantment.BREACH
						? Enchantments.BREACH
						: Enchantments.DENSITY);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(holder, enchantment.level());
		return enchantments.toImmutable();
	}

	private static Component name(String text) {
		return Component.literal(text).withStyle(ChatFormatting.WHITE);
	}

	private static Component line(String text, ChatFormatting colour) {
		return Component.literal(text).withStyle(colour);
	}

	/** The display name of a Wind Burst option. */
	public static String windBurstName(int level) {
		return level == 0 ? "Wind Burst 0" : "Wind Burst " + roman(level);
	}

	/** The display name of a main enchantment option. */
	public static String mainName(DragonMaceConfig.MainEnchantment enchantment) {
		return enchantment == DragonMaceConfig.MainEnchantment.BREACH ? "Breach IV" : "Density V";
	}

	/** Roman numerals for the levels this menu offers. */
	public static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(level);
		};
	}
}
