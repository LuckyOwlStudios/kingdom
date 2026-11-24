package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.kingdom.Diplomacy;
import com.luckyowlstudios.kingdoms.kingdom.KingdomCommand;
import com.luckyowlstudios.kingdoms.kingdom.KingdomScreens;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
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
                .register(entries -> entries.accept(ModItems.TOWN_HALL.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(ModItems.GUIDE_SPAWN_EGG.get()));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> KingdomCommand.register(dispatcher));
        registerNetworking();
    }

    private static void registerNetworking() {
        PayloadTypeRegistry.playS2C().register(OpenCreateKingdomPayload.TYPE, OpenCreateKingdomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CreateKingdomPayload.TYPE, CreateKingdomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(KingdomViewPayload.TYPE, KingdomViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KingdomActionPayload.TYPE, KingdomActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KingdomActionPayload.TYPE,
                (payload, context) -> KingdomScreens.handle(context.player(), payload));
        PayloadTypeRegistry.playS2C().register(DiplomacyViewPayload.TYPE, DiplomacyViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DiplomacyActionPayload.TYPE, DiplomacyActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DiplomacyActionPayload.TYPE,
                (payload, context) -> Diplomacy.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CreateKingdomPayload.TYPE,
                (payload, context) -> Kingdoms.tryCreate(context.player(), payload));
    }
}
