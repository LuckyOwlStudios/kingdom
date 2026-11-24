package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
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
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.item.CreativeModeTabs;

public class KingdomsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        FabricDefaultAttributeRegistry.register(ModEntities.GUIDE.get(), GuideEntity.createAttributes());

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> {
                    entries.accept(ModItems.TOWN_HALL.get());
                    entries.accept(ModItems.SCHOLARS_DESK.get());
                    entries.accept(ModItems.WATCHTOWER.get());
                    entries.accept(ModItems.TAVERN.get());
                    entries.accept(ModItems.HOUSE_MARKER.get());
                });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(ModItems.GUIDE_SPAWN_EGG.get()));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> KingdomCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(KingdomVillagers::tick);
        registerNetworking();
    }

    private static void registerNetworking() {
        PayloadTypeRegistry.playS2C().register(OpenCreateKingdomPayload.TYPE, OpenCreateKingdomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CreateKingdomPayload.TYPE, CreateKingdomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(KingdomViewPayload.TYPE, KingdomViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KingdomActionPayload.TYPE, KingdomActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CloseTownHallPayload.TYPE, CloseTownHallPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KingdomActionPayload.TYPE,
                (payload, context) -> KingdomScreens.handle(context.player(), payload));
        PayloadTypeRegistry.playS2C().register(DiplomacyViewPayload.TYPE, DiplomacyViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DiplomacyActionPayload.TYPE, DiplomacyActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DiplomacyActionPayload.TYPE,
                (payload, context) -> Diplomacy.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CreateKingdomPayload.TYPE,
                (payload, context) -> Kingdoms.tryCreate(context.player(), payload));
        PayloadTypeRegistry.playS2C().register(com.luckyowlstudios.kingdoms.network.ResearchViewPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.ResearchViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(com.luckyowlstudios.kingdoms.network.ResearchActionPayload.TYPE,
                com.luckyowlstudios.kingdoms.network.ResearchActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.luckyowlstudios.kingdoms.network.ResearchActionPayload.TYPE,
                (payload, context) -> com.luckyowlstudios.kingdoms.kingdom.KingdomResearch.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CloseTownHallPayload.TYPE,
                (payload, context) -> TownHallBlockEntity.onScreenClosed(context.player(), payload.townHall()));
    }
}
