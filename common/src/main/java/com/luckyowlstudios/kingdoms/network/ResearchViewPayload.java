package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server to client: what a Scholar's Desk's Research screen shows. Sent when a member opens the desk, and every second
 * while it's open so the progress bar moves.
 *
 * @param desk       the desk
 * @param viewerRank the rank of the player looking; only the leader starts research
 * @param perkPoints perk points left to spend
 * @param perks      the tier the kingdom has of each perk it has unlocked
 * @param research   the tier being researched, if any
 * @param scholars   Scholars working for the kingdom right now
 */
public record ResearchViewPayload(BlockPos desk, String name, DyeColor color, KingdomRank viewerRank, int level,
                                  int perkPoints, Map<ResourceLocation, Integer> perks, Optional<Progress> research,
                                  int scholars) implements CustomPacketPayload {

    public static final Type<ResearchViewPayload> TYPE = new Type<>(Constants.id("research_view"));

    /**
     * @param progress seconds of Scholar work done
     * @param duration seconds of Scholar work it takes
     */
    public record Progress(ResourceLocation perk, int tier, int progress, int duration) {
        static final StreamCodec<RegistryFriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Progress::perk,
                ByteBufCodecs.VAR_INT, Progress::tier,
                ByteBufCodecs.VAR_INT, Progress::progress,
                ByteBufCodecs.VAR_INT, Progress::duration,
                Progress::new);
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<ResourceLocation, Integer>> PERKS_CODEC =
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> {
                BlockPos.STREAM_CODEC.encode(buf, view.desk);
                ByteBufCodecs.STRING_UTF8.encode(buf, view.name);
                DyeColor.STREAM_CODEC.encode(buf, view.color);
                buf.writeVarInt(view.viewerRank.ordinal());
                buf.writeVarInt(view.level);
                buf.writeVarInt(view.perkPoints);
                PERKS_CODEC.encode(buf, view.perks);
                ByteBufCodecs.optional(Progress.STREAM_CODEC).encode(buf, view.research);
                buf.writeVarInt(view.scholars);
            },
            buf -> new ResearchViewPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    DyeColor.STREAM_CODEC.decode(buf), KingdomRank.values()[buf.readVarInt()], buf.readVarInt(), buf.readVarInt(),
                    PERKS_CODEC.decode(buf), ByteBufCodecs.optional(Progress.STREAM_CODEC).decode(buf), buf.readVarInt()));

    /** The tier the kingdom has of a perk, or 0. */
    public int perkTier(ResourceLocation perk) {
        return this.perks.getOrDefault(perk, 0);
    }

    /** The perk being researched, or null. */
    public ResourceLocation researching() {
        return this.research.map(Progress::perk).orElse(null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
