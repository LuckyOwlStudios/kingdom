package com.luckyowlstudios.kingdoms.network;

import io.netty.buffer.ByteBuf;
import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;

/** Client to server: found a kingdom with this name and colour at the Town Hall at {@code townHall}. */
public record CreateKingdomPayload(BlockPos townHall, String name, DyeColor color) implements CustomPacketPayload {

    public static final Type<CreateKingdomPayload> TYPE = new Type<>(Constants.id("create_kingdom"));

    public static final StreamCodec<ByteBuf, CreateKingdomPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CreateKingdomPayload::townHall,
            ByteBufCodecs.stringUtf8(Kingdom.MAX_NAME_LENGTH * 4), CreateKingdomPayload::name,
            DyeColor.STREAM_CODEC, CreateKingdomPayload::color,
            CreateKingdomPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
