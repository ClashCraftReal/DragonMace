package com.lorenz.dragonmace.client;

import com.lorenz.dragonmace.DragonMaceEnchantmentMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the global enchantment configuration.
 *
 * <p>Draws the vanilla chest background — {@code textures/gui/container/generic_54.png} cropped to the
 * three container rows — with the same ten-argument {@code blit} overload vanilla
 * {@code ContainerScreen} uses. No custom texture is shipped and no tint colour is passed, so the
 * panel renders exactly as a real chest does.
 *
 * <p>{@code AbstractContainerScreen.renderContents} never calls {@code renderTooltip}, so the tooltip
 * pass is invoked explicitly after {@code super.render}. Without that call the display stacks render
 * but hovering them shows nothing.
 */
public class DragonMaceEnchantmentScreen extends AbstractContainerScreen<DragonMaceEnchantmentMenu> {
	private static final Identifier CHEST_TEXTURE =
			Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	/** Six chest rows: 17 pixels of border and title, 108 of slots, 7 of bottom border. */
	private static final int PANEL_HEIGHT = 17 + 6 * 18 + 7;

	public DragonMaceEnchantmentScreen(DragonMaceEnchantmentMenu menu, Inventory inventory,
			Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = PANEL_HEIGHT;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		// The menu has no player inventory slots, so the label is pushed off-screen.
		this.inventoryLabelY = this.imageHeight + 1000;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, 256, 256);

		if (!this.menu.canApply()) {
			graphics.drawString(this.font, "Cooldown: " + this.menu.cooldownSeconds() + "s",
					this.leftPos + 104, this.topPos + 6, 0xA00000, false);
		}
	}
}
