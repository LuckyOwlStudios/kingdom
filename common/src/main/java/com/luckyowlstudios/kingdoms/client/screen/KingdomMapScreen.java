package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * The kingdom map on its own screen, opened by clicking the map on the Town Hall's Overview tab. Drag to pan,
 * scroll to zoom, and click an icon to see what it is.
 */
public class KingdomMapScreen extends Screen {

    private static final float MAX_ZOOM = 16.0F;
    private static final int MARGIN = 20;
    private static final int TOP = 28;
    private static final int BOTTOM = 56;

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
        this.addRenderableWidget(Button.builder(Component.translatable("kingdoms.kingdom.map.reset"), button -> {
            this.resetView();
        }).bounds(this.width / 2 - 154, y, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 + 4, y, 150, 20).build());
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

    private Optional<KingdomMapRenderer.Icon> iconAt(double mouseX, double mouseY) {
        if (!this.inView(mouseX, mouseY)) {
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
        if (scrollY == 0 || !this.inView(mouseX, mouseY)) {
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
        KingdomMapRenderer.drawMap(graphics, this.view, mapLeft, mapTop, this.zoom);
        Optional<KingdomMapRenderer.Icon> hovered = this.dragging ? Optional.empty() : this.iconAt(mouseX, mouseY);
        if (KingdomMapRenderer.hasMapData(this.view)) {
            for (KingdomMapRenderer.Icon icon : KingdomMapRenderer.icons(this.view)) {
                KingdomMapRenderer.drawIcon(graphics, icon, Math.round(mapLeft + icon.mapX() * this.zoom),
                        Math.round(mapTop + icon.mapY() * this.zoom), this.iconSize(), icon.equals(this.selected));
            }
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("kingdoms.kingdom.map.unexplored"),
                    (left + right) / 2, (top + bottom) / 2, 0xA0A0A0);
        }
        graphics.flush();
        graphics.disableScissor();

        // What's selected, under the map.
        Component info = this.selected != null ? this.selected.name() : Component.translatable("kingdoms.kingdom.map.help");
        graphics.drawCenteredString(this.font, info, this.width / 2, bottom + 8, this.selected != null ? 0xFFFFFF : 0xA0A0A0);

        hovered.ifPresent(icon -> graphics.renderTooltip(this.font, icon.name(), mouseX, mouseY));
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
