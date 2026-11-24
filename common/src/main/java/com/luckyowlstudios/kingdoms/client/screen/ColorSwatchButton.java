package com.luckyowlstudios.kingdoms.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** One dye colour to pick: a slot-style frame with the colour inside, outlined in white when chosen. */
public class ColorSwatchButton extends AbstractButton {

    public static final int SIZE = 20;
    public static final int GAP = 4;
    public static final int PER_ROW = 8;
    public static final int ROW_WIDTH = PER_ROW * SIZE + (PER_ROW - 1) * GAP;

    private final DyeColor color;
    private final Supplier<DyeColor> selected;
    private final Consumer<DyeColor> onPick;

    public ColorSwatchButton(int x, int y, DyeColor color, Supplier<DyeColor> selected, Consumer<DyeColor> onPick) {
        super(x, y, SIZE, SIZE, name(color));
        this.color = color;
        this.selected = selected;
        this.onPick = onPick;
        this.setTooltip(Tooltip.create(name(color)));
    }

    public static Component name(DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName());
    }

    @Override
    public void onPress() {
        this.onPick.accept(this.color);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        graphics.fill(x, y, x + this.width, y + this.height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + this.width - 1, y + this.height - 1, this.active ? 0xFF8B8B8B : 0xFF555555);
        int fill = this.color.getTextureDiffuseColor() | 0xFF000000;
        if (!this.active) {
            fill = (fill & 0xFEFEFEFE) >>> 1 | 0xFF000000; // dimmed
        }
        graphics.fill(x + 3, y + 3, x + this.width - 3, y + this.height - 3, fill);
        if (this.selected.get() == this.color) {
            graphics.renderOutline(x, y, this.width, this.height, 0xFFFFFFFF);
            graphics.renderOutline(x + 1, y + 1, this.width - 2, this.height - 2, 0xFFFFFFFF);
        } else if (this.active && this.isHoveredOrFocused()) {
            graphics.renderOutline(x, y, this.width, this.height, 0xFFA0A0A0);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
