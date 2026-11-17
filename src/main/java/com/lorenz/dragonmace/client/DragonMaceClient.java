package com.lorenz.dragonmace.client;

import com.lorenz.dragonmace.DragonMaceEnchantments;
import com.lorenz.dragonmace.DragonMaceMenus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint: binds the enchantment menu to its screen and registers the Shift + F shortcut.
 *
 * <p>The shortcut only sends {@code /dm changeenchants}. The server re-checks curse ownership and owns
 * every configuration change, so the keybinding cannot grant access on its own. The key is bound to F
 * and only acts while Shift is held and a mace is in the main hand, so the vanilla offhand-swap binding
 * on plain F still works and Shift + F keeps swapping the offhand for every other held item.
 */
public class DragonMaceClient implements ClientModInitializer {
	public static final String OPEN_MENU_KEY = "key.dragon_mace.changeenchants";

	private static KeyMapping openMenu;

	@Override
	public void onInitializeClient() {
		MenuScreens.register(DragonMaceMenus.ENCHANTMENT, DragonMaceEnchantmentScreen::new);

		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(OPEN_MENU_KEY,
				GLFW.GLFW_KEY_F, KeyMapping.Category.GAMEPLAY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick drains the queue, so one press can only ever send one request.
			while (openMenu.consumeClick()) {
				if (client.player == null) {
					continue;
				}

				// The mace check comes first: with anything else in hand the shortcut is not
				// evaluated at all, so no screen or key state is consulted and nothing is sent.
				boolean holdingMace = DragonMaceEnchantments.isMace(client.player.getMainHandItem());

				if (!holdingMace) {
					continue;
				}

				if (shouldOpen(client.screen != null, client.options.keyShift.isDown(), holdingMace)) {
					client.player.connection.sendCommand("dm changeenchants");
				}
			}
		});
	}

	/** The registered keybinding, or null before client initialisation. */
	public static KeyMapping openMenuKey() {
		return openMenu;
	}

	/**
	 * True while the Shift + F shortcut is held over a mace.
	 *
	 * <p>Read by {@code MinecraftKeybindMixin} to suppress the vanilla offhand swap. The shortcut is
	 * bound to plain {@code F}, the same key vanilla uses for the offhand swap, so without this the
	 * offhand item was swapped on every press before the menu request was even sent.
	 *
	 * <p>The held item is part of the decision: with anything other than a mace in the main hand this
	 * returns {@code false}, so the swap binding is left alone and Shift + F behaves exactly like
	 * vanilla offhand swapping.
	 */
	public static boolean isMenuShortcutDown() {
		Minecraft client = Minecraft.getInstance();

		if (client == null || openMenu == null) {
			return false;
		}

		// Checked before any key state so a non-mace hand never reaches the shortcut logic and the
		// vanilla offhand swap is left completely alone.
		if (!holdsMace(client)) {
			return false;
		}

		return client.options.keyShift.isDown() && openMenu.isDown();
	}

	/** True when the local player's main hand holds a vanilla or Draconic mace. */
	private static boolean holdsMace(Minecraft client) {
		return client.player != null && DragonMaceEnchantments.isMace(client.player.getMainHandItem());
	}

	/**
	 * True when the shortcut should fire for the current input state.
	 *
	 * <p>The mace check is the first thing evaluated and short-circuits everything else, so an empty
	 * hand, paper, a sword or any other item bypasses the shortcut entirely rather than failing a later
	 * condition. Only a mace reaches the screen and Shift checks. Takes plain booleans so the whole
	 * decision is testable without a client.
	 */
	static boolean shouldOpen(boolean screenOpen, boolean shiftDown, boolean holdingMace) {
		if (!holdingMace) {
			return false;
		}

		return !screenOpen && shiftDown;
	}
}
