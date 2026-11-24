package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.client.KingdomsClient;
import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.kingdom.KingdomCommand;
import com.luckyowlstudios.kingdoms.kingdom.KingdomScreens;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.network.KingdomActionPayload;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.luckyowlstudios.kingdoms.network.OpenCreateKingdomPayload;
import com.luckyowlstudios.kingdoms.platform.NeoForgeRegistryHelper;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class KingdomsNeoForge {

    public KingdomsNeoForge(IEventBus eventBus) {
        NeoForgeRegistryHelper.setModBus(eventBus);
        CommonClass.init();

        eventBus.addListener(KingdomsNeoForge::onEntityAttributes);
        eventBus.addListener(KingdomsNeoForge::onBuildCreativeTabs);
        eventBus.addListener(KingdomsNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(KingdomsNeoForge::registerCommands);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUIDE.get(), GuideEntity.createAttributes().build());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.TOWN_HALL.get());
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GUIDE_SPAWN_EGG.get());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // The lambdas keep client classes from loading on a dedicated server.
        registrar.playToClient(OpenCreateKingdomPayload.TYPE, OpenCreateKingdomPayload.STREAM_CODEC,
                (payload, context) -> KingdomsClient.handle(payload));
        registrar.playToServer(CreateKingdomPayload.TYPE, CreateKingdomPayload.STREAM_CODEC,
                (payload, context) -> Kingdoms.tryCreate((ServerPlayer) context.player(), payload));
        registrar.playToClient(KingdomViewPayload.TYPE, KingdomViewPayload.STREAM_CODEC,
                (payload, context) -> KingdomsClient.handle(payload));
        registrar.playToServer(KingdomActionPayload.TYPE, KingdomActionPayload.STREAM_CODEC,
                (payload, context) -> KingdomScreens.handle((ServerPlayer) context.player(), payload));
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        KingdomCommand.register(event.getDispatcher());
    }
}
