package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player closed the Town Hall screen of the hall at {@code townHall}, so its book can close. */
public record CloseTownHallPayload(BlockPos townHall) implements CustomPacketPayload {

    public static final Type<CloseTownHallPayload> TYPE = new Type<>(Constants.id("close_town_hall"));

    public static final StreamCodec<ByteBuf, CloseTownHallPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(CloseTownHallPayload::new, CloseTownHallPayload::townHall);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
