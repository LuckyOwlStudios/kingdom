package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.kingdom.KingdomMap;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws a kingdom's map with its claimed land and icons, at any position and zoom. Used by the small map on the
 * Town Hall's Overview tab and by the full {@link KingdomMapScreen}.
 * <p>
 * Positions are given as the screen position of the map's top-left corner plus a zoom (screen pixels per map
 * pixel). The map is a grid of vanilla maps, each {@link #MAP_SIZE} pixels across at one block per pixel, so
 * growing the kingdom map adds land around the edge rather than changing the scale.
 */
public final class KingdomMapRenderer {

    public static final int MAP_SIZE = 128;
    private static final ResourceLocation MAP_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/map/map_background.png");
    private static final ResourceLocation TOWN_HALL_ICON = Constants.id("textures/map/decorations/townhall.png");
    /** Icon textures are 8x8, like vanilla's map decorations. */
    public static final int ICON_SIZE = 8;

    /**
     * Something marked on the map that can be hovered or selected.
     *
     * @param mapX  where it is, in map pixels from the map's left edge
     * @param mapY  the same, from the top edge
     * @param name    shown when hovered, and at the top of the info panel when selected
     * @param details more lines for the info panel
     * @param picture an item drawn big in the info panel, or empty for none
     */
    public record Icon(ResourceLocation texture, float mapX, float mapY, Component name, List<Component> details,
                       ItemStack picture) {
    }

    private KingdomMapRenderer() {
    }

    /** Map pixels across the whole grid of maps. */
    public static int gridSize(KingdomViewPayload view) {
        return (view.mapRadius() * 2 + 1) * MAP_SIZE;
    }

    /** True once the client has any of the kingdom's maps to draw. */
    public static boolean hasMapData(KingdomViewPayload view) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && view.mapTiles().stream().anyMatch(tile -> minecraft.level.getMapData(tile.id()) != null);
    }

    /**
     * A world X coordinate as a map pixel X, counted from the grid's left edge. Every map is at full detail (one block
     * per pixel) and the middle one is centred on the Town Hall, so its left edge is 64 blocks west of the hall.
     */
    public static float toMapX(KingdomViewPayload view, double worldX) {
        return (float) (worldX - (view.townHall().getX() - MAP_SIZE / 2) + view.mapRadius() * MAP_SIZE);
    }

    /** A world Z coordinate as a map pixel Y, counted from the grid's top edge. */
    public static float toMapY(KingdomViewPayload view, double worldZ) {
        return (float) (worldZ - (view.townHall().getZ() - MAP_SIZE / 2) + view.mapRadius() * MAP_SIZE);
    }

    public static Component coloredName(KingdomViewPayload view) {
        return Component.literal(view.name()).withStyle(style -> style.withColor(view.color().getTextColor()));
    }

    /** The Town Hall icon's name, "<Kingdom>'s Town Hall", with the kingdom's name in its colour. */
    public static Component townHallName(KingdomViewPayload view) {
        return Component.translatable("kingdoms.kingdom.map.town_hall", coloredName(view));
    }

    /** Everything marked on the map. Just the Town Hall for now, in the middle of the grid. */
    public static List<Icon> icons(KingdomViewPayload view) {
        float x = toMapX(view, view.townHall().getX() + 0.5);
        float y = toMapY(view, view.townHall().getZ() + 0.5);
        List<Component> details = List.of(Component.translatable("kingdoms.kingdom.map.panel.level", view.growth().level()));
        return List.of(new Icon(TOWN_HALL_ICON, x, y, townHallName(view), details, view.banner()));
    }

    /** Vanilla's map paper, sized to the whole grid, with the grid's top-left corner at x, y. */
    public static void drawPaper(GuiGraphics graphics, float x, float y, int size, float zoom) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(zoom, zoom, 1.0F);
        graphics.blit(MAP_BACKGROUND, -6, -6, 0, 0, size + 12, size + 12, size + 12, size + 12);
        pose.popPose();
    }

    /**
     * Every map of the grid side by side, then the kingdom's land over them, with the grid's top-left corner at x, y.
     * Callers clip it to what they want to show.
     */
    public static void drawMap(GuiGraphics graphics, KingdomViewPayload view, float x, float y, float zoom) {
        drawMap(graphics, view, x, y, zoom, true);
    }

    /** The same, with the kingdom's land shaded only if {@code showLand} is set. */
    public static void drawMap(GuiGraphics graphics, KingdomViewPayload view, float x, float y, float zoom, boolean showLand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PoseStack pose = graphics.pose();
        int radius = view.mapRadius();
        for (KingdomMap.Tile tile : view.mapTiles()) {
            MapItemSavedData data = minecraft.level.getMapData(tile.id());
            if (data == null) {
                continue;
            }
            pose.pushPose();
            pose.translate(x + (tile.x() + radius) * MAP_SIZE * zoom, y + (tile.z() + radius) * MAP_SIZE * zoom, 1.0F);
            pose.scale(zoom, zoom, 1.0F);
            minecraft.gameRenderer.getMapRenderer().render(pose, graphics.bufferSource(), tile.id(), data, true, 0xF000F0);
            pose.popPose();
        }
        graphics.flush();
        if (showLand) {
            drawClaims(graphics, view, x, y, zoom);
        }
    }

    /**
     * Shades the kingdom's claimed chunks in its colour, with an outline around the edge of its land, so
     * neighbouring claims read as one area. Only the part on the map is drawn.
     */
    private static void drawClaims(GuiGraphics graphics, KingdomViewPayload view, float x, float y, float zoom) {
        Set<ChunkPos> claims = new HashSet<>(view.claims());
        if (claims.isEmpty()) {
            return;
        }
        int rgb = view.color().getTextColor() & 0xFFFFFF;
        int fill = 0x55000000 | rgb;
        int edge = 0xDD000000 | rgb;
        int size = gridSize(view);
        graphics.enableScissor(Mth.floor(x), Mth.floor(y), Mth.ceil(x + size * zoom), Mth.ceil(y + size * zoom));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 2.0F); // above the map
        for (ChunkPos chunk : claims) {
            int x0 = Mth.floor(x + toMapX(view, chunk.getMinBlockX()) * zoom);
            int y0 = Mth.floor(y + toMapY(view, chunk.getMinBlockZ()) * zoom);
            int x1 = Math.max(x0 + 1, Mth.floor(x + toMapX(view, chunk.getMaxBlockX() + 1) * zoom));
            int y1 = Math.max(y0 + 1, Mth.floor(y + toMapY(view, chunk.getMaxBlockZ() + 1) * zoom));
            graphics.fill(x0, y0, x1, y1, fill);
            if (!claims.contains(new ChunkPos(chunk.x, chunk.z - 1))) graphics.fill(x0, y0, x1, y0 + 1, edge);
            if (!claims.contains(new ChunkPos(chunk.x, chunk.z + 1))) graphics.fill(x0, y1 - 1, x1, y1, edge);
            if (!claims.contains(new ChunkPos(chunk.x - 1, chunk.z))) graphics.fill(x0, y0, x0 + 1, y1, edge);
            if (!claims.contains(new ChunkPos(chunk.x + 1, chunk.z))) graphics.fill(x1 - 1, y0, x1, y1, edge);
        }
        pose.popPose();
        graphics.disableScissor();
    }

    /** Draws an icon centred on a screen position, {@code size} pixels across, with a frame if it's selected. */
    public static void drawIcon(GuiGraphics graphics, Icon icon, int centerX, int centerY, int size, boolean selected) {
        int left = centerX - size / 2;
        int top = centerY - size / 2;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 3.0F); // above the map and the claim shading
        if (selected) {
            graphics.renderOutline(left - 2, top - 2, size + 4, size + 4, 0xFFFFFFFF);
        }
        graphics.blit(icon.texture(), left, top, size, size, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        pose.popPose();
    }
}
