package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

/**
 * Client to server: a button pressed in the Town Hall screen. The server checks the player's rank for each one.
 *
 * @param target the player acted on, for member actions
 * @param text   a player name, for {@link Action#INVITE}
 * @param value  a colour id, an inventory slot, or 0/1 for a setting
 */
public record KingdomActionPayload(Action action, Optional<UUID> target, String text, int value) implements CustomPacketPayload {

    public static final Type<KingdomActionPayload> TYPE = new Type<>(Constants.id("kingdom_action"));

    public enum Action {
        PROMOTE, DEMOTE, KICK, BAN, UNBAN, INVITE, SET_COLOR, SET_BANNER, SET_INVITE_ONLY, SET_FRIENDLY_FIRE, LEAVE
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, KingdomActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), KingdomActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), KingdomActionPayload::target,
            ByteBufCodecs.stringUtf8(64), KingdomActionPayload::text,
            ByteBufCodecs.VAR_INT, KingdomActionPayload::value,
            KingdomActionPayload::new);

    public static KingdomActionPayload of(Action action) {
        return new KingdomActionPayload(action, Optional.empty(), "", 0);
    }

    public static KingdomActionPayload on(Action action, UUID target) {
        return new KingdomActionPayload(action, Optional.of(target), "", 0);
    }

    public static KingdomActionPayload withValue(Action action, int value) {
        return new KingdomActionPayload(action, Optional.empty(), "", value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
