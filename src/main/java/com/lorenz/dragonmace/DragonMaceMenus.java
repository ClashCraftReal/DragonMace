package com.lorenz.dragonmace;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Registry entry for the enchantment configuration menu.
 *
 * <p>This is the only registry entry the mod adds. It is registered from {@code BootstrapMixin} at the
 * head of {@code BuiltInRegistries.bootStrap()}, the last point before the registries freeze; doing it
 * from {@code onInitialize} throws {@code This registry can't create intrusive holders}.
 */
public final class DragonMaceMenus {
	public static final Identifier ENCHANTMENT_ID =
			Identifier.fromNamespaceAndPath("dragon_mace", "enchantments");

	public static final MenuType<DragonMaceEnchantmentMenu> ENCHANTMENT =
			Registry.register(BuiltInRegistries.MENU, ENCHANTMENT_ID,
					new MenuType<>((id, inventory) -> new DragonMaceEnchantmentMenu(id, inventory),
							FeatureFlags.VANILLA_SET));

	private DragonMaceMenus() {
	}

	/** Forces class initialisation, which performs the registration. */
	public static void register() {
	}
}
