package com.luckyowlstudios.kingdoms.client;

import com.luckyowlstudios.kingdoms.client.screen.CreateKingdomScreen;
import com.luckyowlstudios.kingdoms.client.screen.KingdomScreen;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.luckyowlstudios.kingdoms.network.OpenCreateKingdomPayload;
import com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload;

/** Client-side packet handling. Only ever loaded on the client. */
public final class KingdomsClient {

    private KingdomsClient() {
    }

    public static void handle(OpenCreateKingdomPayload payload) {
        CreateKingdomScreen.open(payload.townHall());
    }

    public static void handle(KingdomViewPayload payload) {
        KingdomScreen.show(payload);
    }

    public static void handle(PerkUnlockedPayload payload) {
        com.luckyowlstudios.kingdoms.kingdom.KingdomPerks.byId(payload.perk()).ifPresent(perk ->
                net.minecraft.client.Minecraft.getInstance().getToasts().addToast(new PerkToast(perk, payload.tier())));
    }

    public static void handle(com.luckyowlstudios.kingdoms.network.ResearchViewPayload payload) {
        com.luckyowlstudios.kingdoms.client.screen.ResearchScreen.show(payload);
    }

    public static void handle(DiplomacyViewPayload payload) {
        KingdomScreen.showDiplomacy(payload);
    }
}
