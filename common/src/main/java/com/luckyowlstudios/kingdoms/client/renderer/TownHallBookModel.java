package com.luckyowlstudios.kingdoms.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The book on top of the Town Hall. Shaped like vanilla's enchanting table book (two covers hinged at a spine with a
 * block of pages inside each, plus two loose pages that stand up from the page blocks), but with small gaps between the
 * covers, the pages and the spine so no two faces share a plane. Vanilla's book has overlapping faces there, which
 * flicker (z-fight) when the book lies closed. The loose pages are hidden while it's closed, since they'd lie inside
 * the page blocks.
 * <p>
 * Model space, in pixels: the spine runs along y, the covers open about it towards -z and +z, and the closed book
 * extends 6 pixels along +x from the spine. Its texture layout is vanilla's, so it uses {@link TownHallBookTexture}.
 */
public class TownHallBookModel {

    /** How far each cover sits from the middle of the book: half the book's thickness. */
    public static final float COVER_OFFSET = 1.1F;
    /** Gap between the spine and the pages' inner edge. */
    private static final float SPINE_GAP = 0.1F;
    /**
     * Where each loose page rests, from 0 (lying on the left pages) to 1 (on the right pages). Near each side, as the
     * lectern shows them, so they stand slightly off the page blocks.
     */
    private static final float LEFT_LOOSE_PAGE = 0.1F;
    private static final float RIGHT_LOOSE_PAGE = 0.9F;
    /** The loose pages show once the book is open this far (radians), clear of the page blocks. */
    private static final float LOOSE_PAGES_MIN_SPREAD = 0.05F;

    private final ModelPart root;
    private final ModelPart leftCover;
    private final ModelPart rightCover;
    private final ModelPart leftPages;
    private final ModelPart rightPages;
    private final ModelPart leftLoosePage;
    private final ModelPart rightLoosePage;

    public TownHallBookModel() {
        this.root = createLayer().bakeRoot();
        this.leftCover = this.root.getChild("left_cover");
        this.rightCover = this.root.getChild("right_cover");
        this.leftPages = this.root.getChild("left_pages");
        this.rightPages = this.root.getChild("right_pages");
        this.leftLoosePage = this.root.getChild("left_loose_page");
        this.rightLoosePage = this.root.getChild("right_loose_page");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Covers: 6 x 10 and paper-thin. Each one's outer face is its south face once turned into place.
        root.addOrReplaceChild("left_cover", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-6.0F, -5.0F, -0.005F, 6.0F, 10.0F, 0.005F), PartPose.offset(0.0F, 0.0F, -COVER_OFFSET));
        root.addOrReplaceChild("right_cover", CubeListBuilder.create().texOffs(16, 0)
                .addBox(0.0F, -5.0F, -0.005F, 6.0F, 10.0F, 0.005F), PartPose.offset(0.0F, 0.0F, COVER_OFFSET));
        // The spine joins the two covers' edges, just outside the pages.
        // It's grown to reach the covers without stretching its texture.
        root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(12, 0)
                .addBox(-1.0F, -5.0F, -0.005F, 2.0F, 10.0F, 0.005F, new CubeDeformation(COVER_OFFSET - 1.0F, 0.0F, 0.0F)),
                PartPose.rotation(0.0F, Mth.HALF_PI, 0.0F));
        // Pages: a 5 x 8 x 1 block on each side, meeting back to back in the middle.
        root.addOrReplaceChild("left_pages", CubeListBuilder.create().texOffs(0, 10)
                .addBox(SPINE_GAP, -4.0F, -1.0F, 5.0F, 8.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_pages", CubeListBuilder.create().texOffs(12, 10)
                .addBox(SPINE_GAP, -4.0F, 0.0F, 5.0F, 8.0F, 1.0F), PartPose.ZERO);
        // Two single pages, vanilla's flipping pages, which stand up a little from each block of pages when open.
        root.addOrReplaceChild("left_loose_page", CubeListBuilder.create().texOffs(24, 10)
                .addBox(SPINE_GAP, -4.0F, 0.0F, 5.0F, 8.0F, 0.005F), PartPose.ZERO);
        root.addOrReplaceChild("right_loose_page", CubeListBuilder.create().texOffs(24, 10)
                .addBox(SPINE_GAP, -4.0F, 0.0F, 5.0F, 8.0F, 0.005F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /**
     * Opens the book.
     *
     * @param spread how far each half turns away from closed, in radians: 0 closed, about pi/2 lying flat
     */
    public void setupAnim(float spread) {
        this.leftCover.yRot = Mth.PI + spread;
        this.rightCover.yRot = -spread;
        this.leftPages.yRot = spread;
        this.rightPages.yRot = -spread;
        // As in vanilla, the pages slide out from the spine as the book opens so they don't poke through it.
        this.leftPages.x = Mth.sin(spread);
        this.rightPages.x = Mth.sin(spread);
        // As vanilla's flipping pages: 0 lies with the left pages, 1 with the right.
        this.leftLoosePage.yRot = spread - 2.0F * spread * LEFT_LOOSE_PAGE;
        this.rightLoosePage.yRot = spread - 2.0F * spread * RIGHT_LOOSE_PAGE;
        this.leftLoosePage.x = Mth.sin(spread);
        this.rightLoosePage.x = Mth.sin(spread);
        boolean open = spread > LOOSE_PAGES_MIN_SPREAD;
        this.leftLoosePage.visible = open;
        this.rightLoosePage.visible = open;
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        this.root.render(poseStack, consumer, packedLight, packedOverlay);
    }
}
