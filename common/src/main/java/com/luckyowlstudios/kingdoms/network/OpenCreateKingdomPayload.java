package com.luckyowlstudios.kingdoms.network;

import io.netty.buffer.ByteBuf;
import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the "Create Your Kingdom" screen for the Town Hall at {@code townHall}. */
public record OpenCreateKingdomPayload(BlockPos townHall) implements CustomPacketPayload {

    public static final Type<OpenCreateKingdomPayload> TYPE = new Type<>(Constants.id("open_create_kingdom"));

    public static final StreamCodec<ByteBuf, OpenCreateKingdomPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(OpenCreateKingdomPayload::new, OpenCreateKingdomPayload::townHall);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
