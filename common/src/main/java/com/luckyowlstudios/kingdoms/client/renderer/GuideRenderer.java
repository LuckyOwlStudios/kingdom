package com.luckyowlstudios.kingdoms.client.renderer;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the guide with the vanilla villager model: the base villager skin, the plains outfit,
 * then the advisor robe on top.
 */
public class GuideRenderer extends MobRenderer<GuideEntity, VillagerModel<GuideEntity>> {

    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("textures/entity/villager/type/plains.png");
    private static final ResourceLocation PROFESSION = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/villager/profession/advisor.png");

    public GuideRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new OutfitLayer(this, TYPE));
        this.addLayer(new OutfitLayer(this, PROFESSION));
    }

    @Override
    public ResourceLocation getTextureLocation(GuideEntity entity) {
        return BASE;
    }

    @Override
    protected void scale(GuideEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    private static class OutfitLayer extends RenderLayer<GuideEntity, VillagerModel<GuideEntity>> {

        private final ResourceLocation texture;

        OutfitLayer(RenderLayerParent<GuideEntity, VillagerModel<GuideEntity>> parent, ResourceLocation texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, GuideEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            renderColoredCutoutModel(this.getParentModel(), this.texture, poseStack, buffer, packedLight, entity, -1);
        }
    }
}
