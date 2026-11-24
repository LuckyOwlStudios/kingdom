package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.client.TownHallTint;
import com.luckyowlstudios.kingdoms.client.renderer.GuideRenderer;
import com.luckyowlstudios.kingdoms.client.renderer.TownHallRenderer;
import com.luckyowlstudios.kingdoms.client.screen.GuideScreen;
import com.luckyowlstudios.kingdoms.registry.ModBlockEntities;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup. The town hall's cutout render type is set in its block model. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class KingdomsNeoForgeClient {

    public KingdomsNeoForgeClient(IEventBus eventBus) {
        eventBus.addListener(KingdomsNeoForgeClient::onRegisterRenderers);
        eventBus.addListener(KingdomsNeoForgeClient::onRegisterScreens);
        eventBus.addListener(KingdomsNeoForgeClient::onRegisterBlockColors);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUIDE.get(), GuideRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TOWN_HALL.get(), TownHallRenderer::new);
    }

    private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(TownHallTint::getColor, ModBlocks.TOWN_HALL.get());
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GUIDE.get(), GuideScreen::new);
    }
}
