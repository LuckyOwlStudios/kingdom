package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * The kingdom map on its own screen, opened by clicking the map on the Town Hall's Overview tab. Drag to pan,
 * scroll to zoom, and click an icon to see what it is in a panel on the right. Buttons along the bottom hide the
 * kingdom's land border and the icons.
 */
public class KingdomMapScreen extends Screen {

    private static final float MAX_ZOOM = 16.0F;
    private static final int MARGIN = 20;
    private static final int TOP = 28;
    private static final int BOTTOM = 56;
    private static final int PANEL_WIDTH = 130;
    private static final int PANEL_MARGIN = 6;

    /** Remembered while the game is running, so the map opens the way it was left. */
    private static boolean showLand = true;
    private static boolean showIcons = true;

    private final KingdomScreen parent;
    private KingdomViewPayload view;
    /** Screen pixels per map pixel. */
    private float zoom = -1.0F;
    /** The map pixel in the middle of the view. */
    private float panX = Float.NaN;
    private float panY = Float.NaN;
    private boolean dragging;
    private KingdomMapRenderer.Icon selected;

    public KingdomMapScreen(KingdomScreen parent, KingdomViewPayload view) {
        super(Component.translatable("kingdoms.kingdom.map.title", view.name()));
        this.parent = parent;
        this.view = view;
    }

    /** New data from the server: keep the view where it is, and the selection if it's still there. */
    void update(KingdomViewPayload view) {
        // The grid grows on every side, so keep looking at the same land.
        float shift = (view.mapRadius() - this.view.mapRadius()) * KingdomMapRenderer.MAP_SIZE;
        this.panX += shift;
        this.panY += shift;
        this.view = view;
        if (this.selected != null) {
            this.selected = KingdomMapRenderer.icons(view).stream()
                    .filter(icon -> icon.texture().equals(this.selected.texture())).findFirst().orElse(null);
        }
    }

    KingdomScreen parent() {
        return this.parent;
    }

    @Override
    protected void init() {
        if (this.zoom < 0) {
            this.resetView();
        }
        this.zoom = Mth.clamp(this.zoom, this.minZoom(), MAX_ZOOM);
        int y = this.height - 28;
        int width = 98;
        int x = this.width / 2 - (width * 4 + 4 * 3) / 2;
        this.addRenderableWidget(CycleButton.onOffBuilder(showLand).create(x, y, width, 20,
                Component.translatable("kingdoms.kingdom.map.toggle.land"), (button, on) -> showLand = on));
        this.addRenderableWidget(CycleButton.onOffBuilder(showIcons).create(x + (width + 4), y, width, 20,
                Component.translatable("kingdoms.kingdom.map.toggle.icons"), (button, on) -> {
                    showIcons = on;
                    if (!on) {
                        this.selected = null;
                    }
                }));
        this.addRenderableWidget(Button.builder(Component.translatable("kingdoms.kingdom.map.reset"), button -> {
            this.resetView();
        }).bounds(x + (width + 4) * 2, y, width, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(x + (width + 4) * 3, y, width, 20).build());
    }

    // ---- The view ----

    private int viewLeft() {
        return MARGIN;
    }

    private int viewTop() {
        return TOP;
    }

    private int viewRight() {
        return this.width - MARGIN;
    }

    private int viewBottom() {
        return this.height - BOTTOM;
    }

    private boolean inView(double x, double y) {
        return x >= this.viewLeft() && x < this.viewRight() && y >= this.viewTop() && y < this.viewBottom();
    }

    /**
     * The starting zoom, which fits one map (the land right around the Town Hall) in the view. It doesn't depend on
     * how big the kingdom map has grown: a bigger map just has more to pan around to.
     */
    private float fitZoom() {
        return this.fit(KingdomMapRenderer.MAP_SIZE);
    }

    private float fit(int mapPixels) {
        float size = Math.min(this.viewRight() - this.viewLeft(), this.viewBottom() - this.viewTop()) - 16;
        return Math.max(0.25F, size / mapPixels);
    }

    /** Zooming out stops once the whole grid fits. */
    private float minZoom() {
        return Math.min(this.fitZoom(), Math.max(this.fit(KingdomMapRenderer.gridSize(this.view)), 0.25F));
    }

    /** Back to the Town Hall, at the starting zoom. */
    private void resetView() {
        this.zoom = this.fitZoom();
        this.panX = this.panY = KingdomMapRenderer.gridSize(this.view) / 2.0F;
    }

    /** Screen position of the map's top-left corner. */
    private float mapLeft() {
        return (this.viewLeft() + this.viewRight()) / 2.0F - this.panX * this.zoom;
    }

    private float mapTop() {
        return (this.viewTop() + this.viewBottom()) / 2.0F - this.panY * this.zoom;
    }

    private int iconSize() {
        return Mth.clamp(Math.round(KingdomMapRenderer.ICON_SIZE * this.zoom / 2.0F), KingdomMapRenderer.ICON_SIZE, 24);
    }

    /** The info panel's left edge; it sits along the right of the view while an icon is selected. */
    private int panelLeft() {
        return this.viewRight() - PANEL_MARGIN - PANEL_WIDTH;
    }

    private boolean inPanel(double x, double y) {
        return this.selected != null && x >= this.panelLeft() && x < this.viewRight() - PANEL_MARGIN
                && y >= this.viewTop() + PANEL_MARGIN && y < this.viewBottom() - PANEL_MARGIN;
    }

    private Optional<KingdomMapRenderer.Icon> iconAt(double mouseX, double mouseY) {
        if (!showIcons || !this.inView(mouseX, mouseY) || this.inPanel(mouseX, mouseY)) {
            return Optional.empty();
        }
        float reach = this.iconSize() / 2.0F + 2;
        List<KingdomMapRenderer.Icon> icons = KingdomMapRenderer.icons(this.view);
        // Last drawn is on top, so check from the end.
        for (int i = icons.size() - 1; i >= 0; i--) {
            KingdomMapRenderer.Icon icon = icons.get(i);
            float x = this.mapLeft() + icon.mapX() * this.zoom;
            float y = this.mapTop() + icon.mapY() * this.zoom;
            if (Math.abs(mouseX - x) <= reach && Math.abs(mouseY - y) <= reach) {
                return Optional.of(icon);
            }
        }
        return Optional.empty();
    }

    private void clampPan() {
        int size = KingdomMapRenderer.gridSize(this.view);
        this.panX = Mth.clamp(this.panX, 0, size);
        this.panY = Mth.clamp(this.panY, 0, size);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.inPanel(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && this.inView(mouseX, mouseY)) {
            // Clicking empty map clears the selection; dragging from there pans.
            this.selected = this.iconAt(mouseX, mouseY).orElse(null);
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.panX -= (float) (dragX / this.zoom);
            this.panY -= (float) (dragY / this.zoom);
            this.clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !this.inView(mouseX, mouseY) || this.inPanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // Zoom around the cursor: the map pixel under it stays under it.
        float pointX = (float) ((mouseX - this.mapLeft()) / this.zoom);
        float pointY = (float) ((mouseY - this.mapTop()) / this.zoom);
        this.zoom = Mth.clamp(this.zoom * (float) Math.pow(1.25, scrollY), this.minZoom(), MAX_ZOOM);
        this.panX = pointX - (float) (mouseX - (this.viewLeft() + this.viewRight()) / 2.0) / this.zoom;
        this.panY = pointY - (float) (mouseY - (this.viewTop() + this.viewBottom()) / 2.0) / this.zoom;
        this.clampPan();
        return true;
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int left = this.viewLeft();
        int top = this.viewTop();
        int right = this.viewRight();
        int bottom = this.viewBottom();
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        graphics.fill(left, top, right, bottom, 0xFF2B2B2B);
        graphics.enableScissor(left, top, right, bottom);
        float mapLeft = this.mapLeft();
        float mapTop = this.mapTop();
        KingdomMapRenderer.drawPaper(graphics, mapLeft, mapTop, KingdomMapRenderer.gridSize(this.view), this.zoom);
        KingdomMapRenderer.drawMap(graphics, this.view, mapLeft, mapTop, this.zoom, showLand);
        Optional<KingdomMapRenderer.Icon> hovered = this.dragging ? Optional.empty() : this.iconAt(mouseX, mouseY);
        if (KingdomMapRenderer.hasMapData(this.view)) {
            for (KingdomMapRenderer.Icon icon : showIcons ? KingdomMapRenderer.icons(this.view) : List.<KingdomMapRenderer.Icon>of()) {
                KingdomMapRenderer.drawIcon(graphics, icon, Math.round(mapLeft + icon.mapX() * this.zoom),
                        Math.round(mapTop + icon.mapY() * this.zoom), this.iconSize(), icon.equals(this.selected));
            }
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("kingdoms.kingdom.map.unexplored"),
                    (left + right) / 2, (top + bottom) / 2, 0xA0A0A0);
        }
        graphics.flush();
        if (this.selected != null) {
            this.renderPanel(graphics, this.selected);
        }
        graphics.disableScissor();

        graphics.drawCenteredString(this.font, Component.translatable("kingdoms.kingdom.map.help"), this.width / 2, bottom + 8, 0xA0A0A0);

        hovered.ifPresent(icon -> graphics.renderTooltip(this.font, icon.name(), mouseX, mouseY));
    }

    /** The selected icon's name, picture and details, in a panel along the right of the view. */
    private void renderPanel(GuiGraphics graphics, KingdomMapRenderer.Icon icon) {
        int left = this.panelLeft();
        int top = this.viewTop() + PANEL_MARGIN;
        int right = left + PANEL_WIDTH;
        int bottom = this.viewBottom() - PANEL_MARGIN;
        int centerX = left + PANEL_WIDTH / 2;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 10.0F); // above the map, its land and its icons
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        graphics.fill(left, top, right, bottom, 0xE0202020);

        int y = top + 6;
        for (FormattedCharSequence line : this.font.split(icon.name(), PANEL_WIDTH - 10)) {
            graphics.drawCenteredString(this.font, line, centerX, y, 0xFFFFFF);
            y += 10;
        }
        y += 4;
        if (!icon.picture().isEmpty()) {
            // Drawn big, like the banner on the Overview tab.
            int size = 48;
            graphics.fill(centerX - size / 2 - 2, y - 2, centerX + size / 2 + 2, y + size + 2, 0xFF000000);
            graphics.fill(centerX - size / 2 - 1, y - 1, centerX + size / 2 + 1, y + size + 1, 0xFF8B8B8B);
            pose.pushPose();
            pose.translate(centerX - size / 2.0F, y, 0.0F);
            pose.scale(3.0F, 3.0F, 1.0F);
            graphics.renderItem(icon.picture(), 0, 0);
            pose.popPose();
            y += size + 8;
        }
        for (Component detail : icon.details()) {
            for (FormattedCharSequence line : this.font.split(detail, PANEL_WIDTH - 10)) {
                graphics.drawString(this.font, line, left + 6, y, 0xA0A0A0);
                y += 10;
            }
        }
        pose.popPose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
