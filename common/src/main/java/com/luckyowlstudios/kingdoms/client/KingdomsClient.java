package com.luckyowlstudios.kingdoms.client;

import com.luckyowlstudios.kingdoms.client.screen.CreateKingdomScreen;
import com.luckyowlstudios.kingdoms.client.screen.KingdomScreen;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import com.luckyowlstudios.kingdoms.network.OpenCreateKingdomPayload;

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
}
