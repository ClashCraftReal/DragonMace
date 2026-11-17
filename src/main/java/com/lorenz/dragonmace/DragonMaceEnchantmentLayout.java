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
 * The fixed 27-slot layout of the Draconic Mace enchantment menu.
 *
 * <p>Slots 0–8 are the empty top row, 9–17 the selection row and 18–26 the action row. The layout is a
 * plain static table so it can be asserted by tests without constructing a menu or a player.
 *
 * <p>Every stack is display-only: the menu refuses all slot interaction, so nothing here can be taken,
 * placed, swapped or shift-clicked.
 */
public final class DragonMaceEnchantmentLayout {
	public static final int SLOT_COUNT = 27;

	public static final int SLOT_WIND_BURST_0 = 9;
	public static final int SLOT_WIND_BURST_1 = 10;
	public static final int SLOT_WIND_BURST_2 = 11;
	public static final int SLOT_WIND_BURST_3 = 12;
	public static final int SLOT_BREACH = 15;
	public static final int SLOT_DENSITY = 16;
	public static final int SLOT_CONFIRM = 21;
	public static final int SLOT_INFO = 22;
	public static final int SLOT_CANCEL = 23;

	/** First slot of the selection row, in slot order. */
	private static final int[] WIND_BURST_SLOTS = {
			SLOT_WIND_BURST_0, SLOT_WIND_BURST_1, SLOT_WIND_BURST_2, SLOT_WIND_BURST_3
	};

	private DragonMaceEnchantmentLayout() {
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
	 * Builds the 27 display stacks for one selection.
	 *
	 * @param registries used for the enchanted-book components; may be null in a bare test
	 * @param windBurst  the selected Wind Burst level, 0–3
	 * @param main       the selected main enchantment
	 */
	public static ItemStack[] build(RegistryAccess registries, int windBurst,
			DragonMaceConfig.MainEnchantment main) {
		ItemStack[] items = new ItemStack[SLOT_COUNT];

		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			items[slot] = ItemStack.EMPTY;
		}

		int level = DragonMaceConfig.clampWindBurst(windBurst);
		DragonMaceConfig.MainEnchantment selectedMain = main == null
				? DragonMaceConfig.DEFAULT_MAIN_ENCHANTMENT
				: main;

		items[SLOT_WIND_BURST_0] = windBurstOption(0, level == 0);
		items[SLOT_WIND_BURST_1] = windBurstOption(1, level == 1);
		items[SLOT_WIND_BURST_2] = windBurstOption(2, level == 2);
		items[SLOT_WIND_BURST_3] = windBurstOption(3, level == 3);

		items[SLOT_BREACH] = mainOption(registries, DragonMaceConfig.MainEnchantment.BREACH,
				selectedMain == DragonMaceConfig.MainEnchantment.BREACH);
		items[SLOT_DENSITY] = mainOption(registries, DragonMaceConfig.MainEnchantment.DENSITY,
				selectedMain == DragonMaceConfig.MainEnchantment.DENSITY);

		items[SLOT_CONFIRM] = confirmOption(level, selectedMain);
		items[SLOT_INFO] = infoOption();
		items[SLOT_CANCEL] = cancelOption();

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

	private static ItemStack confirmOption(int windBurst, DragonMaceConfig.MainEnchantment main) {
		ItemStack stack = new ItemStack(Items.LIME_DYE);
		stack.set(DataComponents.CUSTOM_NAME, name("Confirm"));

		List<Component> lore = new ArrayList<>();
		lore.add(line("Apply Wind Burst " + roman(windBurst) + " and " + mainName(main) + ".",
				ChatFormatting.GRAY));
		lore.add(line("Applies to every mace the curse holder carries.", ChatFormatting.GRAY));
		lore.add(line("Starts a 120 second server-wide cooldown.", ChatFormatting.GRAY));

		return withLore(stack, lore);
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
