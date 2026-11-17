package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Everything the Draconic Mace lore is derived from.
 *
 * <p>The lore is never stored independently of this context: it is rebuilt from the live enchantment
 * component, the player's rune selection and the player's kill count whenever any of them changes, so an
 * outdated enchantment or rune line cannot survive on the item.
 *
 * <p>The enchantment summary is carried as text as well as as a component, because the summary has to be
 * renderable from the global configuration alone (no registry access) for the no-player creation path.
 * Enchantment selections and rune selections are deliberately separate: the summary is bold gold, while
 * each rune line carries its own rune colour and is never bold.
 */
public record DragonMaceLoreContext(ItemEnchantments enchantments, String summary,
		DragonMaceRuneSelection runes, int kills, DragonMaceRuneToggles toggles) {
	public DragonMaceLoreContext {
		enchantments = enchantments == null ? ItemEnchantments.EMPTY : enchantments;
		summary = summary == null ? "" : summary;
		runes = DragonMaceRuneSelection.orDefaults(runes);
		kills = Math.max(0, kills);
		toggles = DragonMaceRuneToggles.orDefaults(toggles);
	}

	/** A context with every rune category enabled. */
	public DragonMaceLoreContext(ItemEnchantments enchantments, String summary,
			DragonMaceRuneSelection runes, int kills) {
		this(enchantments, summary, runes, kills, DragonMaceRuneToggles.defaults());
	}

	/** The context of a mace that has no player behind it: configured enchantments, defaults, no kills. */
	public static DragonMaceLoreContext defaults(RegistryAccess registries) {
		ItemEnchantments configured = DragonMaceEnchantments.configured(registries, DragonMaceConfig.get());
		return new DragonMaceLoreContext(configured, summaryOf(configured),
				DragonMaceRuneSelection.defaults(), 0, DragonMaceRuneToggles.defaults());
	}

	/**
	 * The context of a mace built from the global configuration alone.
	 *
	 * <p>Used where no registry access is available, so the summary still matches the configured
	 * enchantments instead of rendering as empty.
	 */
	public static DragonMaceLoreContext fromConfig(DragonMaceConfig config) {
		DragonMaceConfig settings = config == null ? DragonMaceConfig.defaults() : config;
		int windBurst = settings.windBurstLevel();
		boolean breach = settings.mainEnchantment() == DragonMaceConfig.MainEnchantment.BREACH;

		return new DragonMaceLoreContext(ItemEnchantments.EMPTY,
				summaryOf(windBurst, breach ? 0 : settings.mainEnchantment().level(),
						breach ? settings.mainEnchantment().level() : 0),
				DragonMaceRuneSelection.defaults(), 0, DragonMaceRuneToggles.defaults());
	}

	/** The same context with a different kill count. */
	public DragonMaceLoreContext withKills(int value) {
		return new DragonMaceLoreContext(this.enchantments, this.summary, this.runes, value, this.toggles);
	}

	/** The same context with a different rune selection. */
	public DragonMaceLoreContext withRunes(DragonMaceRuneSelection selection) {
		return new DragonMaceLoreContext(this.enchantments, this.summary, selection, this.kills, this.toggles);
	}

	/** The same context with different rune toggles. */
	public DragonMaceLoreContext withToggles(DragonMaceRuneToggles value) {
		return new DragonMaceLoreContext(this.enchantments, this.summary, this.runes, this.kills, value);
	}

	/** The same context with a different enchantment component, and a summary rebuilt from it. */
	public DragonMaceLoreContext withEnchantments(ItemEnchantments value) {
		return new DragonMaceLoreContext(value, summaryOf(value), this.runes, this.kills, this.toggles);
	}

	/** The bold gold enchantment summary, e.g. {@code Wind Burst III, Density V}. */
	public String enchantmentSummary() {
		return this.summary;
	}

	/** The level of one enchantment in this context, or 0 when it is absent. */
	public int levelOf(ResourceKey<Enchantment> key) {
		for (var entry : this.enchantments.entrySet()) {
			if (entry.getKey().is(key)) {
				return entry.getIntValue();
			}
		}

		return 0;
	}

	/** Builds the summary from a live enchantment component. */
	public static String summaryOf(ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return "";
		}

		int windBurst = 0;
		int density = 0;
		int breach = 0;

		for (var entry : enchantments.entrySet()) {
			if (entry.getKey().is(Enchantments.WIND_BURST)) {
				windBurst = entry.getIntValue();
			} else if (entry.getKey().is(Enchantments.DENSITY)) {
				density = entry.getIntValue();
			} else if (entry.getKey().is(Enchantments.BREACH)) {
				breach = entry.getIntValue();
			}
		}

		return summaryOf(windBurst, density, breach);
	}

	/**
	 * Builds the summary from explicit levels.
	 *
	 * <p>Wind Burst comes first, then the single main enchantment, matching the design order. All zero
	 * yields an empty string, which the lore builder renders as {@code No enchantments}.
	 */
	public static String summaryOf(int windBurst, int density, int breach) {
		List<String> parts = new ArrayList<>(2);

		if (windBurst > 0) {
			parts.add("Wind Burst " + roman(windBurst));
		}

		if (density > 0) {
			parts.add("Density " + roman(density));
		}

		if (breach > 0) {
			parts.add("Breach " + roman(breach));
		}

		return String.join(", ", parts);
	}

	/** Roman numerals for the levels this mod can produce. */
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
