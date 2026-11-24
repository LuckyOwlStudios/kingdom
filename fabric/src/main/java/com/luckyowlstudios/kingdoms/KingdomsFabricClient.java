package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.client.KingdomsClient;
import com.luckyowlstudios.kingdoms.client.TownHallTint;
import com.luckyowlstudios.kingdoms.client.renderer.GuideRenderer;
import com.luckyowlstudios.kingdoms.client.renderer.TownHallRenderer;
import com.luckyowlstudios.kingdoms.client.screen.GuideScreen;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.luckyowlstudios.kingdoms.network.OpenCreateKingdomPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlockEntities;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class KingdomsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TOWN_HALL.get(), RenderType.cutout());
        EntityRendererRegistry.register(ModEntities.GUIDE.get(), GuideRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.TOWN_HALL.get(), TownHallRenderer::new);
        ColorProviderRegistry.BLOCK.register(TownHallTint::getColor, ModBlocks.TOWN_HALL.get());
        MenuScreens.register(ModMenus.GUIDE.get(), GuideScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(OpenCreateKingdomPayload.TYPE, (payload, context) -> KingdomsClient.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(KingdomViewPayload.TYPE, (payload, context) -> KingdomsClient.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(DiplomacyViewPayload.TYPE, (payload, context) -> KingdomsClient.handle(payload));
    }
}
