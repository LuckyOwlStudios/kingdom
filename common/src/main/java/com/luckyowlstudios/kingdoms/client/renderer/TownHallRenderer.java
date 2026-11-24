package com.luckyowlstudios.kingdoms.client.renderer;

import com.luckyowlstudios.kingdoms.block.TownHallBlock;
import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a brown book ({@link TownHallBookModel}, {@link TownHallBookTexture}) lying on top of a Town Hall that belongs to a kingdom. It lies closed with its
 * spine on the reader's left, and the cover swings open while someone has the Town Hall screen open.
 */
public class TownHallRenderer implements BlockEntityRenderer<TownHallBlockEntity> {

    private static final float PIXEL = 1.0F / 16.0F;
    /**
     * How far each half turns when fully open, in radians. A quarter turn each lays both covers flat on the table: the
     * back cover stays put and the front cover swings a full 180 degrees over the spine.
     */
    private static final float MAX_SPREAD = Mth.HALF_PI;
    /** Where the book's centre sits, from the middle of the top: towards the front, clear of the inkwell at the back. */
    private static final float FORWARD = 2.0F * PIXEL;
    /** The closed book is 6 pixels wide; this moves it so it's centred whether open or closed. */
    private static final float HALF_WIDTH = 3.0F * PIXEL;

    private final TownHallBookModel bookModel = new TownHallBookModel();

    public TownHallRenderer(BlockEntityRendererProvider.Context context) {
        // Renderers are made on every resource reload, so this also picks up resource pack changes.
        TownHallBookTexture.register();
    }

    @Override
    public void render(TownHallBlockEntity hall, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!hall.hasKingdom()) {
            return;
        }
        BlockState state = hall.getBlockState();
        Direction facing = state.hasProperty(TownHallBlock.FACING) ? state.getValue(TownHallBlock.FACING) : Direction.NORTH;
        float progress = hall.bookOpenness(partialTick);
        progress = progress * progress * (3.0F - 2.0F * progress); // ease in and out
        float spread = progress * MAX_SPREAD;

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0F, 0.5F);
        // As on the lectern: +x now points out of the front of the block, towards the reader, and +z to their left.
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.getClockWise().toYRot()));
        // The back cover hangs below the spine by up to half the book's thickness: lift the spine so the cover rests
        // just above the table runner.
        poseStack.translate(FORWARD, PIXEL * (TownHallBookModel.COVER_OFFSET * Mth.cos(spread) + 0.05F), HALF_WIDTH * (1.0F - progress));
        // Lay the book down with its pages facing up and its spine running away from the reader...
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        // ...then roll it about the spine so the back cover stays flat on the table while the front cover lifts.
        poseStack.mulPose(Axis.YP.rotation(Mth.HALF_PI - spread));
        this.bookModel.setupAnim(spread);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(TownHallBookTexture.LOCATION));
        // The hall itself is solid, so light the book from the block above it.
        int light = hall.getLevel() != null ? LevelRenderer.getLightColor(hall.getLevel(), hall.getBlockPos().above()) : packedLight;
        this.bookModel.render(poseStack, consumer, light, packedOverlay);
        poseStack.popPose();
    }
}
