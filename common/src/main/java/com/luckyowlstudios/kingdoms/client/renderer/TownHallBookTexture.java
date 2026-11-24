package com.luckyowlstudios.kingdoms.client.renderer;

import com.luckyowlstudios.kingdoms.Constants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;

/**
 * The Town Hall book's texture: vanilla's enchanting table book, read from the loaded resource packs, with its
 * orange-tan leather turned a darker brown. The gold corners, the spine's highlight and the pages stay as they are.
 * <p>
 * Built when resources load (and again on every reload, so a resource pack's book is recoloured too), rather than
 * shipping a copy of the vanilla texture.
 */
public class TownHallBookTexture extends SimpleTexture {

    public static final ResourceLocation LOCATION = Constants.id("textures/entity/town_hall_book.png");
    private static final ResourceLocation VANILLA_BOOK = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");

    /** The covers and spine fill the top-left 28 x 10 of the 64 x 32 texture; the pages are below them. */
    private static final float COVERS_WIDTH = 28.0F / 64.0F;
    private static final float COVERS_HEIGHT = 10.0F / 32.0F;

    /** Leather is orange-tan, around 31 degrees of hue; gold is brighter and yellower, page and spine whites are grey. */
    private static final float LEATHER_MIN_HUE = 20.0F / 360.0F;
    private static final float LEATHER_MAX_HUE = 38.0F / 360.0F;
    private static final float LEATHER_MIN_SATURATION = 0.4F;
    private static final float LEATHER_MAX_VALUE = 0.9F;
    /** The brown it becomes: redder, a little greyer and darker, like the book item. */
    private static final float BROWN_HUE = 22.0F / 360.0F;
    private static final float SATURATION_SCALE = 0.85F;
    private static final float VALUE_SCALE = 0.72F;

    private TownHallBookTexture() {
        super(VANILLA_BOOK);
    }

    /** Registers (or rebuilds) the texture. Call on the render thread, e.g. when the renderer is created on reload. */
    public static void register() {
        Minecraft.getInstance().getTextureManager().register(LOCATION, new TownHallBookTexture());
    }

    @Override
    protected TextureImage getTextureImage(ResourceManager resourceManager) {
        TextureImage texture = super.getTextureImage(resourceManager);
        try {
            NativeImage image = texture.getImage();
            if (image.format() == NativeImage.Format.RGBA) {
                recolor(image);
            }
        } catch (IOException e) {
            Constants.LOG.warn("Couldn't recolour the Town Hall book; it will look like the enchanting table's", e);
        }
        return texture;
    }

    private static void recolor(NativeImage image) {
        int width = Math.round(image.getWidth() * COVERS_WIDTH);
        int height = Math.round(image.getHeight() * COVERS_HEIGHT);
        float[] hsv = new float[3];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // NativeImage packs pixels as ABGR.
                int abgr = image.getPixelRGBA(x, y);
                int alpha = abgr >>> 24;
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                toHsv(r, g, b, hsv);
                if (alpha == 0 || hsv[0] < LEATHER_MIN_HUE || hsv[0] > LEATHER_MAX_HUE
                        || hsv[1] < LEATHER_MIN_SATURATION || hsv[2] >= LEATHER_MAX_VALUE) {
                    continue;
                }
                int rgb = Mth.hsvToRgb(BROWN_HUE, hsv[1] * SATURATION_SCALE, hsv[2] * VALUE_SCALE);
                int nr = (rgb >> 16) & 0xFF;
                int ng = (rgb >> 8) & 0xFF;
                int nb = rgb & 0xFF;
                image.setPixelRGBA(x, y, (alpha << 24) | (nb << 16) | (ng << 8) | nr);
            }
        }
    }

    /** Hue, saturation and value, each 0 to 1. */
    private static void toHsv(int r, int g, int b, float[] hsv) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) / 6.0F;
        } else if (max == g) {
            hue = ((b - r) / delta + 2.0F) / 6.0F;
        } else {
            hue = ((r - g) / delta + 4.0F) / 6.0F;
        }
        hsv[0] = hue < 0.0F ? hue + 1.0F : hue;
        hsv[1] = max == 0 ? 0.0F : delta / max;
        hsv[2] = max / 255.0F;
    }
}
