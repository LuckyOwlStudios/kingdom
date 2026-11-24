package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.luckyowlstudios.kingdoms.client.KingdomsClient;
import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.kingdom.Diplomacy;
import com.luckyowlstudios.kingdoms.kingdom.KingdomCommand;
import com.luckyowlstudios.kingdoms.kingdom.KingdomScreens;
import com.luckyowlstudios.kingdoms.kingdom.KingdomVillagers;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.network.CloseTownHallPayload;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyActionPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> KingdomVillagers.tick(event.getServer()));
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUIDE.get(), GuideEntity.createAttributes().build());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.TOWN_HALL.get());
            event.accept(ModItems.SCHOLARS_DESK.get());
            event.accept(ModItems.WATCHTOWER.get());
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
        registrar.playToClient(com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload.STREAM_CODEC,
                (payload, context) -> KingdomsClient.handle(payload));
        registrar.playToServer(KingdomActionPayload.TYPE, KingdomActionPayload.STREAM_CODEC,
                (payload, context) -> KingdomScreens.handle((ServerPlayer) context.player(), payload));
        registrar.playToClient(DiplomacyViewPayload.TYPE, DiplomacyViewPayload.STREAM_CODEC,
                (payload, context) -> KingdomsClient.handle(payload));
        registrar.playToServer(DiplomacyActionPayload.TYPE, DiplomacyActionPayload.STREAM_CODEC,
                (payload, context) -> Diplomacy.handle((ServerPlayer) context.player(), payload));
        registrar.playToClient(com.luckyowlstudios.kingdoms.network.ResearchViewPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.ResearchViewPayload.STREAM_CODEC,
                (payload, context) -> KingdomsClient.handle(payload));
        registrar.playToServer(com.luckyowlstudios.kingdoms.network.ResearchActionPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.ResearchActionPayload.STREAM_CODEC,
                (payload, context) -> com.luckyowlstudios.kingdoms.kingdom.KingdomResearch.handle((ServerPlayer) context.player(), payload));
        registrar.playToServer(CloseTownHallPayload.TYPE, CloseTownHallPayload.STREAM_CODEC,
                (payload, context) -> TownHallBlockEntity.onScreenClosed((ServerPlayer) context.player(), payload.townHall()));
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        KingdomCommand.register(event.getDispatcher());
    }
}
