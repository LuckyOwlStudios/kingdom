package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.client.renderer.GuideRenderer;
import com.luckyowlstudios.kingdoms.client.screen.GuideScreen;
import com.luckyowlstudios.kingdoms.client.screen.TownHallScreen;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class KingdomsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TOWN_HALL.get(), RenderType.cutout());
        EntityRendererRegistry.register(ModEntities.GUIDE.get(), GuideRenderer::new);
        MenuScreens.register(ModMenus.TOWN_HALL.get(), TownHallScreen::new);
        MenuScreens.register(ModMenus.GUIDE.get(), GuideScreen::new);
    }
}
