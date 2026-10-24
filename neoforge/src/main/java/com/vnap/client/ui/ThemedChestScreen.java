package com.vnap.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/** A chest in one of the mod's looks: the vanilla chest's layout, drawn with the kit (Ui). */
public final class ThemedChestScreen extends AbstractContainerScreen<ChestMenu> {
	private final Ui.Theme theme;

	public ThemedChestScreen(ChestMenu menu, Inventory inventory, Component title, Ui.Theme theme) {
		//? if >=26.1 {
		/*super(menu, inventory, title, 176, 114 + menu.getRowCount() * 18);
		*///?} else {
		super(menu, inventory, title);
		imageHeight = 114 + menu.getRowCount() * 18;
		//?}
		this.theme = theme;
		inventoryLabelY = imageHeight - 94;
	}

	public static ThemedChestScreen village(ChestMenu menu, Inventory inventory, Component title) {
		return new ThemedChestScreen(menu, inventory, title, Ui.Theme.VILLAGE);
	}

	public static ThemedChestScreen spire(ChestMenu menu, Inventory inventory, Component title) {
		return new ThemedChestScreen(menu, inventory, title, Ui.Theme.SPIRE);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		Ui.panel(graphics, theme, leftPos, topPos, imageWidth, imageHeight);
		for (Slot slot : menu.slots) Ui.slot(graphics, theme, leftPos + slot.x, topPos + slot.y);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, theme.title(), false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme.muted(), false);
	}
}
