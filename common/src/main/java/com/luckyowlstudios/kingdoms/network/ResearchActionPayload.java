package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a button in a Scholar's Desk's Research screen, or the screen closing. The server checks the
 * player is at the desk, on their kingdom's land, and is the leader.
 *
 * @param desk the desk the screen belongs to
 * @param perk the perk id, for {@link Action#START}
 */
public record ResearchActionPayload(Action action, BlockPos desk, String perk) implements CustomPacketPayload {

    public static final Type<ResearchActionPayload> TYPE = new Type<>(Constants.id("research_action"));

    public enum Action {
        /** Research the next tier of {@code perk}. */
        START,
        /** Stop the current research and get its points back. */
        CANCEL,
        /** The screen was closed: stop sending updates. */
        CLOSE
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), ResearchActionPayload::action,
            BlockPos.STREAM_CODEC, ResearchActionPayload::desk,
            ByteBufCodecs.stringUtf8(64), ResearchActionPayload::perk,
            ResearchActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
