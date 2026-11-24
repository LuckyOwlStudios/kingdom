package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's kingdom unlocked a perk (tier 1) or upgraded one, so the client shows a toast.
 */
public record PerkUnlockedPayload(ResourceLocation perk, int tier) implements CustomPacketPayload {

    public static final Type<PerkUnlockedPayload> TYPE = new Type<>(Constants.id("perk_unlocked"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PerkUnlockedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PerkUnlockedPayload::perk,
            ByteBufCodecs.VAR_INT, PerkUnlockedPayload::tier,
            PerkUnlockedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
