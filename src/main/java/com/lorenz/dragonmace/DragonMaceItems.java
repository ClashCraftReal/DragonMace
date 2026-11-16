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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

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
	 * The vanilla Heavy Core model, applied through the synced {@code minecraft:item_model} component.
	 *
	 * <p>This is what gives the paper item the Heavy Core appearance without shipping a client asset
	 * and without a custom registry entry: the client resolves the vanilla model by id.
	 */
	public static final Identifier VERY_HEAVY_CORE_MODEL =
			Identifier.withDefaultNamespace("block/heavy_core");

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
				.append(styled("Totems stop working on you.", ChatFormatting.DARK_AQUA)));
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

	/** The lore of the Draconic Mace, in the same visual language as the core. */
	public static List<Component> draconicMaceLore() {
		List<Component> lines = new ArrayList<>(11);

		lines.add(styled("Curse of the Dragons (\u221E):", ChatFormatting.GOLD));
		lines.add(Component.empty()
				.append(styled("Dragons Wrath:", ChatFormatting.GOLD))
				.append(styled(" This mace answers only to the cursed.", ChatFormatting.DARK_AQUA)));
		lines.add(Component.empty());
		lines.add(Component.empty()
				.append(styled("Smash:", ChatFormatting.GOLD))
				.append(styled(" Slam the ground to launch nearby foes.", ChatFormatting.DARK_AQUA)));
		lines.add(Component.empty());
		lines.add(styled("\"Insanely heavy Singularity,", ChatFormatting.LIGHT_PURPLE));
		lines.add(styled("forged with ancient mass and the remains of a dragon. ", ChatFormatting.LIGHT_PURPLE));
		lines.add(styled("From it's shadow, this weapon arises.\"", ChatFormatting.LIGHT_PURPLE));
		lines.add(Component.empty());
		lines.add(bold("DRACONIC MACE", ChatFormatting.GOLD));

		return List.copyOf(lines);
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
	 * enchantments the player already had are preserved. The placeholder enchantments are only applied
	 * when the mace carries none, and that decision is recorded so a withdrawal can remove exactly what
	 * was added.
	 */
	public static ItemStack createDraconicMaceStack(ItemStack source, RegistryAccess registries) {
		ItemStack stack = source == null || source.isEmpty() ? new ItemStack(Items.MACE) : source.copy();

		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		tag.putBoolean(DragonMaceIdentity.DRACONIC_KEY, true);
		tag.putInt(DragonMaceIdentity.DRACONIC_RUNE_KEY, DRACONIC_RUNE_LEVEL);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME,
				styled(DragonMaceRecipes.DRAGON_MACE_NAME, ChatFormatting.DARK_PURPLE));
		stack.set(DataComponents.LORE, new ItemLore(draconicMaceLore()));
		stack.set(DataComponents.RARITY, Rarity.EPIC);

		applyPlaceholderEnchantments(stack, registries);
		return stack;
	}

	/**
	 * Applies the temporary Draconic Mace enchantments.
	 *
	 * <p>Density V and Wind Burst III are real vanilla enchantments. Draconic Rune has no vanilla
	 * equivalent, so its level is stored in custom data under
	 * {@link DragonMaceIdentity#DRACONIC_RUNE_KEY} for the future enchantment system to read. A mace
	 * that already has enchantments is left alone so a player's own enchantments are never overwritten.
	 *
	 * @return {@code true} when the placeholder enchantments were written by this call
	 */
	public static boolean applyPlaceholderEnchantments(ItemStack stack, RegistryAccess registries) {
		if (stack == null || stack.isEmpty() || registries == null) {
			return false;
		}

		if (EnchantmentHelper.hasAnyEnchantments(stack)) {
			return false;
		}

		HolderLookup.RegistryLookup<Enchantment> registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
		Holder.Reference<Enchantment> density = registry.getOrThrow(Enchantments.DENSITY);
		Holder.Reference<Enchantment> windBurst = registry.getOrThrow(Enchantments.WIND_BURST);

		EnchantmentHelper.updateEnchantments(stack, enchantments -> {
			enchantments.set(density, DENSITY_LEVEL);
			enchantments.set(windBurst, WIND_BURST_LEVEL);
		});

		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		tag.putBoolean(DragonMaceIdentity.PLACEHOLDER_ENCHANTMENTS_KEY, true);
		tag.putInt(DragonMaceIdentity.DRACONIC_RUNE_KEY, DRACONIC_RUNE_LEVEL);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return true;
	}

	/**
	 * Reverts a Draconic Mace to the mace it was before the conversion.
	 *
	 * <p>Only the components this update added are removed, so the stack itself is never destroyed and
	 * a pre-existing Dragon Mace marker keeps the unique mace intact.
	 */
	public static ItemStack revertDraconicMace(ItemStack source) {
		ItemStack stack = source.copy();
		CompoundTag tag = DragonMaceIdentity.copyTag(stack);
		boolean placeholder = tag.getBooleanOr(DragonMaceIdentity.PLACEHOLDER_ENCHANTMENTS_KEY, false);

		tag.remove(DragonMaceIdentity.DRACONIC_KEY);
		tag.remove(DragonMaceIdentity.PLACEHOLDER_ENCHANTMENTS_KEY);
		tag.remove(DragonMaceIdentity.DRACONIC_RUNE_KEY);

		if (tag.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}

		if (placeholder) {
			stack.remove(DataComponents.ENCHANTMENTS);
		}

		stack.remove(DataComponents.RARITY);
		stack.remove(DataComponents.LORE);

		if (DragonMaceIdentity.isDragonMace(stack)) {
			stack.set(DataComponents.CUSTOM_NAME,
					styled(DragonMaceRecipes.DRAGON_MACE_NAME, ChatFormatting.DARK_PURPLE));
		} else {
			stack.remove(DataComponents.CUSTOM_NAME);
		}

		return stack;
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
