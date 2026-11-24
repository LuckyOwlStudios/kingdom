package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.DyeColor;

/**
 * "Create Your Kingdom": a name box, the sixteen dye colours to pick from, and Create / Cancel.
 * Built only from vanilla widgets so it looks like the rest of the game.
 */
public class CreateKingdomScreen extends Screen {

    private static final Component NAME_LABEL = Component.translatable("kingdoms.kingdom.create.name");
    private static final Component COLOR_LABEL = Component.translatable("kingdoms.kingdom.create.color");
    private static final int SWATCH_SIZE = 20;
    private static final int SWATCH_GAP = 4;
    private static final int SWATCHES_PER_ROW = 8;

    private final BlockPos townHall;
    private EditBox nameBox;
    private Button createButton;
    private DyeColor color = DyeColor.BLUE;

    public CreateKingdomScreen(BlockPos townHall) {
        super(Component.translatable("kingdoms.kingdom.create.title"));
        this.townHall = townHall;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 80;

        String previous = this.nameBox != null ? this.nameBox.getValue() : "";
        this.nameBox = new EditBox(this.font, centerX - 100, top + 34, 200, 20, NAME_LABEL);
        this.nameBox.setMaxLength(Kingdom.MAX_NAME_LENGTH);
        this.nameBox.setValue(previous);
        this.nameBox.setResponder(value -> this.updateCreateButton());
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        int rowWidth = SWATCHES_PER_ROW * SWATCH_SIZE + (SWATCHES_PER_ROW - 1) * SWATCH_GAP;
        int swatchLeft = centerX - rowWidth / 2;
        int swatchTop = top + 82;
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            int x = swatchLeft + (i % SWATCHES_PER_ROW) * (SWATCH_SIZE + SWATCH_GAP);
            int y = swatchTop + (i / SWATCHES_PER_ROW) * (SWATCH_SIZE + SWATCH_GAP);
            this.addRenderableWidget(new ColorSwatchButton(x, y, colors[i], () -> this.color, picked -> this.color = picked));
        }

        int buttonTop = swatchTop + 2 * (SWATCH_SIZE + SWATCH_GAP) + 12;
        this.createButton = this.addRenderableWidget(Button.builder(Component.translatable("kingdoms.kingdom.create.confirm"), button -> this.create())
                .bounds(centerX - 102, buttonTop, 100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(centerX + 2, buttonTop, 100, 20).build());
        this.updateCreateButton();
    }

    private void updateCreateButton() {
        if (this.createButton != null) {
            this.createButton.active = !this.nameBox.getValue().isBlank();
        }
    }

    private void create() {
        String name = this.nameBox.getValue().strip();
        if (name.isEmpty() || this.minecraft == null || this.minecraft.getConnection() == null) {
            return;
        }
        this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new CreateKingdomPayload(this.townHall, name, this.color)));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter in the name box creates the kingdom, like vanilla's create-world screen.
        if ((keyCode == 257 || keyCode == 335) && this.createButton.active) {
            this.create();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Vanilla's menu blur plus a dark tint, so the kingdom UI stands out from the world behind it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int top = this.height / 2 - 80;
        graphics.drawCenteredString(this.font, this.title, centerX, top, 0xFFFFFF);
        graphics.drawString(this.font, NAME_LABEL, centerX - 100, top + 22, 0xA0A0A0);
        graphics.drawString(this.font, COLOR_LABEL, centerX - 100, top + 70, 0xA0A0A0);
        // Preview of the name in the chosen colour, as it will show in chat.
        String name = this.nameBox.getValue().strip();
        if (!name.isEmpty()) {
            graphics.drawString(this.font, Component.literal(name), centerX + 100 - this.font.width(name), top + 70,
                    this.color.getTextColor() | 0xFF000000);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(BlockPos townHall) {
        Minecraft.getInstance().setScreen(new CreateKingdomScreen(townHall));
    }

}
