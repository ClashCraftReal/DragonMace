package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Builds the two new item identities of the Dragon effect update.
 *
 * <p>Both are plain vanilla items carrying a persistent {@code minecraft:custom_data} marker, exactly
 * like the existing Dragon Mace, so the mod registers no items and stays server-side only. The Very
 * Heavy Core is a {@code minecraft:paper} stamped with {@link DragonMaceIdentity#VERY_HEAVY_CORE_KEY};
 * the Draconic Mace is a {@code minecraft:mace} that additionally carries
 * {@link DragonMaceIdentity#DRACONIC_KEY}.
 */
public final class DragonMaceItems {
	public static final String VERY_HEAVY_CORE_NAME = "Very Heavy Core";

	/**
	 * The custom identifier stamped into {@code minecraft:custom_data}.
	 *
	 * <p>This is what distinguishes the Very Heavy Core from ordinary {@code minecraft:paper}; the
	 * item type alone cannot, because both are paper.
	 */
	public static final Identifier VERY_HEAVY_CORE_ID =
			Identifier.fromNamespaceAndPath("dragon_mace", "very_heavy_core");

	/**
	 * The vanilla Heavy Core item definition, applied through the synced {@code minecraft:item_model}
	 * component.
	 *
	 * <p>The component takes an <em>item definition</em> id, not a block model id. The client resolves
	 * it from {@code assets/<namespace>/items/<path>.json}, so the value must be
	 * {@code minecraft:heavy_core}, which resolves to {@code assets/minecraft/items/heavy_core.json}.
	 * That definition in turn points at {@code minecraft:block/heavy_core}, which is what actually
	 * renders. Using {@code minecraft:block/heavy_core} here would look for
	 * {@code assets/minecraft/items/block/heavy_core.json}, which does not exist, and the item would
	 * fall back to the plain paper model.
	 */
	public static final Identifier VERY_HEAVY_CORE_MODEL =
			Identifier.withDefaultNamespace("heavy_core");

	/** Placeholder enchantment levels, replaced later by the dedicated enchantment system. */
	public static final int DENSITY_LEVEL = 5;
	public static final int WIND_BURST_LEVEL = 3;
	public static final int DRACONIC_RUNE_LEVEL = 3;

	private DragonMaceItems() {
	}

	private static MutableComponent styled(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
	}

	private static MutableComponent bold(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true).withItalic(false));
	}

	/**
	 * The exact lore of the Very Heavy Core, reproduced line for line from the design command.
	 *
	 * <p>Line order, colours, bold runs and the {@code LEGNDARY} spelling are intentional and must not
	 * be normalised.
	 */
	public static List<Component> veryHeavyCoreLore() {
		List<Component> lines = new ArrayList<>(19);

		lines.add(styled("Consumable", ChatFormatting.DARK_GRAY));
		lines.add(Component.empty());
		lines.add(styled("Using this on your mace may grant you the following effect:", ChatFormatting.DARK_AQUA));
		lines.add(Component.empty());
		lines.add(styled("Curse of the Dragons (\u221E):", ChatFormatting.GOLD));
		lines.add(styled("Gain ancient abilities once inhibited by dragons,", ChatFormatting.DARK_AQUA));
		lines.add(styled("granting the following effects:", ChatFormatting.DARK_AQUA));
		lines.add(Component.empty()
				.append(styled("Dragons Wrath:", ChatFormatting.GOLD))
				.append(styled(" Convert maces in your inventory to the ", ChatFormatting.DARK_AQUA))
				.append(bold("Draconic Mace", ChatFormatting.LIGHT_PURPLE)));
		lines.add(Component.empty()
				.append(styled("Dragons Aura:", ChatFormatting.GOLD))
				.append(styled(" Gain permanent glowing and a dragon Aura.", ChatFormatting.DARK_AQUA)));
			lines.add(Component.empty()
					.append(styled("Dragons Pride: ", ChatFormatting.GOLD))
					.append(styled("You may carry and use up to two Totems of Undying.", ChatFormatting.DARK_AQUA)));
		lines.add(styled("Curse transfers to killer on death.", ChatFormatting.DARK_AQUA));
		lines.add(Component.empty());
		lines.add(styled("\"Insanely heavy Singularity,", ChatFormatting.LIGHT_PURPLE));
		lines.add(styled("forged with ancient mass and the remains of a dragon. ", ChatFormatting.LIGHT_PURPLE));
		lines.add(styled("From it's shadow, this weapon arises.\"", ChatFormatting.LIGHT_PURPLE));
		lines.add(Component.empty());
		lines.add(styled("Right-click to consume", ChatFormatting.GREEN));
		lines.add(Component.empty());
		lines.add(bold("LEGNDARY CONSUMABLE", ChatFormatting.GOLD));

		return List.copyOf(lines);
	}

	/** The five gold stars of the Draconic Mace name. */
	public static final String DRACONIC_STARS = "\u272A\u272A\u272A\u272A\u272A";
	/** The red circled five that closes the Draconic Mace name. */
	public static final String DRACONIC_TIER = "\u278E";
	/** The rune glyph that opens the Draconic Rune lore line. */
	public static final String DRACONIC_RUNE_GLYPH = "\u25C6";

	/**
	 * The exact Draconic Mace name, reproduced from the design command.
	 *
	 * <p>Four runs: the light purple name, a plain space, five gold stars and the red tier glyph. Every
	 * run is explicitly non-italic, because vanilla italicises a custom name by default.
	 */
	public static Component draconicMaceName() {
		return Component.empty()
				.append(styled("Draconic Mace", ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)))
				.append(styled(DRACONIC_STARS, ChatFormatting.GOLD))
				.append(styled(DRACONIC_TIER, ChatFormatting.RED));
	}

	private static MutableComponent obfuscated(String text, ChatFormatting color) {
		return Component.literal(text)
				.withStyle(Style.EMPTY.withColor(color).withBold(true).withItalic(false).withObfuscated(true));
	}

	/**
	 * The exact Draconic Mace lore, reproduced line for line from the design command.
	 *
	 * <p>Five content sections separated by blank lines: the gear score, the enchantment summary, the
	 * Draconic Rune line, Soulbound and the Mythic Mace footer. Colours, bold runs, the obfuscated
	 * {@code X} characters and the trailing spaces are intentional and must not be normalised.
	 */
	public static List<Component> draconicMaceLore() {
		List<Component> lines = new ArrayList<>(19);

		lines.add(Component.empty()
				.append(styled("Gear Score: ", ChatFormatting.GRAY))
				.append(styled("9999 ", ChatFormatting.LIGHT_PURPLE))
				.append(styled("(9999)", ChatFormatting.DARK_AQUA)));
		lines.add(Component.empty());
		lines.add(bold("Wind Burst III, Density V", ChatFormatting.GOLD));
		lines.add(Component.empty());
		lines.add(Component.empty()
				.append(styled("Ability: Enchant Chooser", ChatFormatting.GOLD))
				.append(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)))
				.append(bold("SNEAK OFFHAND", ChatFormatting.GREEN)));
		lines.add(styled("Allows the user to enchant their mace at will,", ChatFormatting.GRAY));
		lines.add(styled("using dragon powers.", ChatFormatting.GRAY));
		lines.add(styled("Opens a GUI to choose enchantments and runes.", ChatFormatting.GRAY));
		lines.add(Component.empty()
				.append(styled("Cooldown:", ChatFormatting.DARK_GRAY))
				.append(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)))
				.append(styled("120s", ChatFormatting.YELLOW)));
		lines.add(Component.empty());
		lines.add(Component.empty()
				.append(styled("Ability: Smash ", ChatFormatting.GOLD))
				.append(bold("LEFT CLICK", ChatFormatting.GREEN)));
		lines.add(Component.empty()
				.append(styled("While falling over ", ChatFormatting.GRAY))
				.append(styled("1.5 blocks", ChatFormatting.GOLD))
				.append(styled(", activate a smash attack.", ChatFormatting.GRAY)));
		lines.add(Component.empty()
				.append(styled("Deals more ", ChatFormatting.GRAY))
				.append(styled("damage", ChatFormatting.RED))
				.append(styled(" the higher the fall.", ChatFormatting.GRAY)));
		lines.add(Component.empty());
		lines.add(styled(DRACONIC_RUNE_GLYPH + " Draconic Rune III", ChatFormatting.WHITE));
		lines.add(Component.empty());
		lines.add(styled("*Converted by Curse of the Dragons*", ChatFormatting.DARK_GRAY));
		lines.add(styled("*Soulbound*", ChatFormatting.DARK_GRAY));
		lines.add(Component.empty()
				.append(obfuscated("X", ChatFormatting.LIGHT_PURPLE))
				.append(bold(" Mythic Mace ", ChatFormatting.LIGHT_PURPLE))
				.append(obfuscated("X", ChatFormatting.LIGHT_PURPLE)));

		return List.copyOf(lines);
	}

	/**
	 * The hidden tooltip components of the Draconic Mace.
	 *
	 * <p>Attribute modifiers, enchantments and unbreakable are hidden so the custom lore is the only
	 * place the enchantments are shown, exactly as the design command specifies.
	 */
	public static TooltipDisplay draconicTooltipDisplay() {
		return TooltipDisplay.DEFAULT
				.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
				.withHidden(DataComponents.ENCHANTMENTS, true)
				.withHidden(DataComponents.UNBREAKABLE, true);
	}

	/**
	 * A fresh stack of the Very Heavy Core: {@code minecraft:paper} carrying the custom identifier.
	 *
	 * <p>Every component is written onto the stack itself, so the identity survives being dropped,
	 * picked up, stored in a container, transferred between players and a server restart.
	 */
	public static ItemStack createVeryHeavyCoreStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		applyVeryHeavyCoreIdentity(stack);
		return stack;
	}

	/**
	 * Stamps the Very Heavy Core identity onto a paper stack.
	 *
	 * <p>Used by the recipe result, which is built as an already-stamped stack directly, so a crafted
	 * core is never plain paper even for one tick.
	 *
	 * @return {@code true} when the stack was plain paper and is now the Very Heavy Core
	 */
	public static boolean applyVeryHeavyCoreIdentity(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.PAPER) || DragonMaceIdentity.isVeryHeavyCore(stack)) {
			return false;
		}

		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		tag.putBoolean(DragonMaceIdentity.VERY_HEAVY_CORE_KEY, true);
		tag.putString(DragonMaceIdentity.VERY_HEAVY_CORE_ID_KEY, VERY_HEAVY_CORE_ID.toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, styled(VERY_HEAVY_CORE_NAME, ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(veryHeavyCoreLore()));
		stack.set(DataComponents.ITEM_MODEL, VERY_HEAVY_CORE_MODEL);
		return true;
	}

	/**
	 * Converts a mace into the Draconic Mace.
	 *
	 * <p>Every existing component is copied first, so damage, a pre-existing Dragon Mace marker and any
	 * enchantments the player already had are preserved. The design components are then written on top:
	 * the exact name and lore, the forced glint, unbreakable and the hidden tooltip components, plus the
	 * real Density V and Wind Burst III enchantments.
	 */
	public static ItemStack createDraconicMaceStack(ItemStack source, RegistryAccess registries) {
		ItemStack stack = source == null || source.isEmpty() ? new ItemStack(Items.MACE) : source.copy();

		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		tag.putBoolean(DragonMaceIdentity.DRACONIC_KEY, true);
		tag.remove(DragonMaceIdentity.REVERTED_KEY);
		tag.putInt(DragonMaceIdentity.DRACONIC_RUNE_KEY, DRACONIC_RUNE_LEVEL);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		// The canonical name is always restored, whatever the player renamed the reverted mace to.
		stack.set(DataComponents.CUSTOM_NAME, draconicMaceName());
		stack.set(DataComponents.LORE, new ItemLore(draconicMaceLore()));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		stack.set(DataComponents.TOOLTIP_DISPLAY, draconicTooltipDisplay());
		stack.set(DataComponents.ENCHANTABLE, new Enchantable(1));
		// updateEnchantments returns early when the component is absent, so seed it first.
		stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

		applyPlaceholderEnchantments(stack, registries);
		return stack;
	}

	/**
	 * Applies the Draconic Mace enchantments.
	 *
	 * <p>Density V and Wind Burst III are real vanilla enchantments and are always written, so the
	 * design is identical on every mace. Draconic Rune has no vanilla equivalent, so its level is stored
	 * in custom data under {@link DragonMaceIdentity#DRACONIC_RUNE_KEY} for the future enchantment
	 * system to read; it stays presentation-only and is never registered as an enchantment.
	 *
	 * @return {@code true} when the enchantments were written by this call
	 */
	public static boolean applyPlaceholderEnchantments(ItemStack stack, RegistryAccess registries) {
		if (stack == null || stack.isEmpty() || registries == null) {
			return false;
		}

		// The global configuration decides the enchantments, so a Draconic Mace always matches it.
		stack.set(DataComponents.ENCHANTMENTS,
				DragonMaceEnchantments.configured(registries, DragonMaceConfig.get()));

		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		tag.putBoolean(DragonMaceIdentity.PLACEHOLDER_ENCHANTMENTS_KEY, true);
		tag.putInt(DragonMaceIdentity.DRACONIC_RUNE_KEY, DRACONIC_RUNE_LEVEL);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return true;
	}

	/**
	 * Reverts a Draconic Mace to a plain mace that keeps only its custom name.
	 *
	 * <p>Every Draconic property is stripped: lore, enchantments, the glint override, unbreakable, the
	 * tooltip display, the item model and the custom data marker. {@code ENCHANTABLE} is removed too,
	 * which is what makes {@code ItemStack.isEnchantable()} false and therefore blocks the enchanting
	 * table server-side rather than only in the client UI.
	 *
	 * <p>The stack itself is never destroyed and its damage is preserved. The custom name is kept
	 * exactly as it was, so the item still reads as a Draconic Mace while carrying none of its power.
	 * The unique Stage 1 Dragon Mace keeps its own name, because its marker is not Draconic-specific.
	 */
	public static ItemStack revertDraconicMace(ItemStack source) {
		ItemStack stack = source.copy();
		CompoundTag tag = DragonMaceIdentity.copyTag(stack);

		tag.remove(DragonMaceIdentity.DRACONIC_KEY);
		tag.remove(DragonMaceIdentity.PLACEHOLDER_ENCHANTMENTS_KEY);
		tag.remove(DragonMaceIdentity.DRACONIC_RUNE_KEY);
		// Kept so the anvil guard still recognises the item after a rename replaces its name.
		tag.putBoolean(DragonMaceIdentity.REVERTED_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		stack.remove(DataComponents.ENCHANTMENTS);
		stack.remove(DataComponents.STORED_ENCHANTMENTS);
		stack.remove(DataComponents.ENCHANTABLE);
		stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		stack.remove(DataComponents.UNBREAKABLE);
		stack.remove(DataComponents.TOOLTIP_DISPLAY);
		stack.remove(DataComponents.RARITY);
		stack.remove(DataComponents.LORE);

		// Restore the item's own default model instead of deleting the component. The mace prototype
		// carries minecraft:item_model=minecraft:mace, and a stack without it has no model to resolve,
		// which is what made a dropped mace render invisible.
		Identifier defaultModel = Items.MACE.components().get(DataComponents.ITEM_MODEL);

		if (defaultModel == null) {
			stack.remove(DataComponents.ITEM_MODEL);
		} else {
			stack.set(DataComponents.ITEM_MODEL, defaultModel);
		}

		// A reverted mace is an ordinary mace again, so it carries no custom name at all. The unique
		// Stage 1 Dragon Mace is a different item and keeps its own canonical name.
		if (DragonMaceIdentity.isDragonMace(stack)) {
			stack.set(DataComponents.CUSTOM_NAME,
					styled(DragonMaceRecipes.DRAGON_MACE_NAME, ChatFormatting.DARK_PURPLE));
		} else {
			stack.remove(DataComponents.CUSTOM_NAME);
		}

		return stack;
	}

	/**
	 * True when {@code result} carries an enchantment level above what {@code input} already had.
	 *
	 * <p>This is the anvil guard's decision, kept here so it can be tested without a live menu. A pure
	 * rename leaves every level unchanged and is therefore allowed; adding a book, merging with another
	 * enchanted mace or any other merge that raises a level is refused.
	 */
	public static boolean addsEnchantments(ItemStack input, ItemStack result) {
		if (input == null || result == null || result.isEmpty()) {
			return false;
		}

		ItemEnchantments before = input.getEnchantments();
		ItemEnchantments after = result.getEnchantments();

		for (Holder<Enchantment> enchantment : after.keySet()) {
			if (after.getLevel(enchantment) > before.getLevel(enchantment)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Consumes one Very Heavy Core and activates the curse.
	 *
	 * <p>The stack is only reduced after the curse was actually written, so a refused activation can
	 * never eat a core.
	 */
	public static InteractionResult consumeVeryHeavyCore(ServerPlayer player, ItemStack stack) {
		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return InteractionResult.PASS;
		}

		switch (DragonMaceCurse.activate(server, player)) {
			case ACTIVATED -> {
				stack.shrink(1);
				return InteractionResult.SUCCESS;
			}
			case ALREADY_ACTIVE -> {
				player.sendSystemMessage(Component.literal("You already hold the Curse of the Dragons.")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				return InteractionResult.FAIL;
			}
			case TAKEN -> {
				player.sendSystemMessage(Component.literal(
								"The Curse of the Dragons is already active on another player.")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				return InteractionResult.FAIL;
			}
			default -> {
				return InteractionResult.PASS;
			}
		}
	}
}
