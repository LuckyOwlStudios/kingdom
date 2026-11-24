package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client to server: a button pressed in the Town Hall's Diplomacy tab, acting on another kingdom.
 * The server checks the player's rank and the kingdoms' current relation for each one.
 */
public record DiplomacyActionPayload(Action action, UUID kingdom) implements CustomPacketPayload {

    public static final Type<DiplomacyActionPayload> TYPE = new Type<>(Constants.id("diplomacy_action"));

    public enum Action {
        REQUEST_ALLIANCE, CANCEL_REQUEST, ACCEPT_ALLIANCE, DECLINE_ALLIANCE, END_ALLIANCE, DECLARE_ENEMY, MAKE_PEACE
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DiplomacyActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), DiplomacyActionPayload::action,
            UUIDUtil.STREAM_CODEC, DiplomacyActionPayload::kingdom,
            DiplomacyActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
