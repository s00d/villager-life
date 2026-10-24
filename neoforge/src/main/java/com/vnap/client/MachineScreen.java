package com.vnap.client;

import com.vnap.block.decor.MachineMenu;
import com.vnap.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Every machine's window, a furnace of the village: the inputs, the arrow of the work, the output, the fuel's flame. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final Ui.Theme THEME = Ui.Theme.VILLAGE;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(graphics, THEME, x, y, imageWidth, imageHeight);
        Ui.header(graphics, THEME, font, x + 4, y + 4, imageWidth - 8, title);
        int output = menu.machine().inputs();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (i == output) Ui.bigSlot(graphics, THEME, x + slot.x, y + slot.y);
            else Ui.slot(graphics, THEME, x + slot.x, y + slot.y);
        }
        int total = menu.total();
        Ui.arrow(graphics, THEME, x + MachineMenu.ARROW_X, y + MachineMenu.ROW_Y, total > 0 ? (double) menu.progress() / total : 0.0);
        if (menu.machine().fuelSlot() >= 0) {
            Ui.flame(graphics, THEME, x + MachineMenu.INPUT_X + menu.machine().fuelSlot() * 18 + 1, y + MachineMenu.ROW_Y + 19, menu.fuel() > 0 ? 1.0 : 0.0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, THEME.muted(), false);
    }
}
