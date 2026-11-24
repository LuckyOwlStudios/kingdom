package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Draws a kingdom's map with its claimed land and icons, at any position and zoom. Used by the small map on the
 * Town Hall's Overview tab and by the full {@link KingdomMapScreen}.
 * <p>
 * Positions are given as the screen position of the map's top-left corner plus a zoom (screen pixels per map
 * pixel); the map itself is {@link #MAP_SIZE} map pixels across.
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
     * @param name  shown when hovered, and in the info panel when selected
     */
    public record Icon(ResourceLocation texture, float mapX, float mapY, Component name) {
    }

    private KingdomMapRenderer() {
    }

    public static Optional<MapItemSavedData> mapData(KingdomViewPayload view) {
        Minecraft minecraft = Minecraft.getInstance();
        return view.map().map(id -> minecraft.level == null ? null : minecraft.level.getMapData(id));
    }

    /** The block at the map's left (or top) edge, worked out the same way vanilla fills a map in (MapItem#update). */
    private static int worldEdge(KingdomViewPayload view, int center) {
        int scale = 1 << view.mapScale();
        return (center / scale - MAP_SIZE / 2) * scale;
    }

    /** A world X coordinate as a map pixel X. */
    public static float toMapX(KingdomViewPayload view, double worldX) {
        return (float) ((worldX - worldEdge(view, view.mapCenterX())) / (1 << view.mapScale()));
    }

    /** A world Z coordinate as a map pixel Y. */
    public static float toMapY(KingdomViewPayload view, double worldZ) {
        return (float) ((worldZ - worldEdge(view, view.mapCenterZ())) / (1 << view.mapScale()));
    }

    public static Component coloredName(KingdomViewPayload view) {
        return Component.literal(view.name()).withStyle(style -> style.withColor(view.color().getTextColor()));
    }

    /** Everything marked on the map. Just the Town Hall for now, kept on the map's edge if it's off the map. */
    public static List<Icon> icons(KingdomViewPayload view) {
        float half = ICON_SIZE / 2.0F;
        float x = Mth.clamp(toMapX(view, view.townHall().getX() + 0.5), half, MAP_SIZE - half);
        float y = Mth.clamp(toMapY(view, view.townHall().getZ() + 0.5), half, MAP_SIZE - half);
        return List.of(new Icon(TOWN_HALL_ICON, x, y, coloredName(view)));
    }

    /** Vanilla's map paper, the map's pixels, and the kingdom's land, with the map's top-left corner at x, y. */
    public static void drawMap(GuiGraphics graphics, KingdomViewPayload view, float x, float y, float zoom) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(zoom, zoom, 1.0F);
        graphics.blit(MAP_BACKGROUND, -6, -6, 0, 0, MAP_SIZE + 12, MAP_SIZE + 12, MAP_SIZE + 12, MAP_SIZE + 12);
        Optional<MapItemSavedData> data = mapData(view);
        if (data.isPresent()) {
            pose.translate(0.0F, 0.0F, 1.0F);
            Minecraft.getInstance().gameRenderer.getMapRenderer().render(pose, graphics.bufferSource(), view.map().get(),
                    data.get(), true, 0xF000F0);
        }
        pose.popPose();
        graphics.flush();
        if (data.isPresent()) {
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
        graphics.enableScissor(Mth.floor(x), Mth.floor(y), Mth.ceil(x + MAP_SIZE * zoom), Mth.ceil(y + MAP_SIZE * zoom));
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
