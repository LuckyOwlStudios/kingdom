package com.luckyowlstudios.kingdoms.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A plain panel with a title and a "coming soon" line. Used until the real UIs are designed.
 */
public class PlaceholderScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final Component COMING_SOON = Component.translatable("gui.kingdoms.coming_soon");

    public PlaceholderScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF3B2A1A);
        graphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFFC6A776);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, (this.imageWidth - this.font.width(this.title)) / 2, 8, 0x3B2A1A, false);
        graphics.drawString(this.font, COMING_SOON, (this.imageWidth - this.font.width(COMING_SOON)) / 2, this.imageHeight / 2, 0x5A4630, false);
    }
}
