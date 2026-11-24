package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server to client: everything the Town Hall screen shows about a kingdom. Sent when a member opens the
 * Town Hall, and again after each change they make so the screen stays current.
 *
 * @param viewerRank the rank of the player looking, which decides which buttons work
 * @param members    members and banned players; banned ones have rank {@link KingdomRank#NONE}
 * @param mapCenterX the block the kingdom map is centred on (clients don't get this with the map itself)
 * @param claims     the kingdom's claimed chunks in the map's dimension, to draw on the map
 * @param claimCount how many chunks the kingdom holds in every dimension
 */
public record KingdomViewPayload(BlockPos townHall, String name, DyeColor color, ItemStack banner,
                                 Optional<MapId> map, int mapScale, int mapCenterX, int mapCenterZ,
                                 List<ChunkPos> claims, int claimCount, boolean inviteOnly, boolean friendlyFire,
                                 KingdomRank viewerRank, List<Member> members) implements CustomPacketPayload {

    public static final Type<KingdomViewPayload> TYPE = new Type<>(Constants.id("kingdom_view"));

    public record Member(UUID id, String name, KingdomRank rank, boolean banned) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.VAR_INT.map(i -> KingdomRank.values()[i], KingdomRank::ordinal), Member::rank,
                ByteBufCodecs.BOOL, Member::banned,
                Member::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, KingdomViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> {
                BlockPos.STREAM_CODEC.encode(buf, view.townHall);
                ByteBufCodecs.STRING_UTF8.encode(buf, view.name);
                DyeColor.STREAM_CODEC.encode(buf, view.color);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.banner);
                ByteBufCodecs.optional(MapId.STREAM_CODEC).encode(buf, view.map);
                buf.writeVarInt(view.mapScale);
                buf.writeInt(view.mapCenterX);
                buf.writeInt(view.mapCenterZ);
                buf.writeVarInt(view.claims.size());
                view.claims.forEach(chunk -> buf.writeLong(chunk.toLong()));
                buf.writeVarInt(view.claimCount);
                buf.writeBoolean(view.inviteOnly);
                buf.writeBoolean(view.friendlyFire);
                buf.writeVarInt(view.viewerRank.ordinal());
                buf.writeVarInt(view.members.size());
                view.members.forEach(member -> Member.STREAM_CODEC.encode(buf, member));
            },
            buf -> {
                BlockPos townHall = BlockPos.STREAM_CODEC.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                DyeColor color = DyeColor.STREAM_CODEC.decode(buf);
                ItemStack banner = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                Optional<MapId> map = ByteBufCodecs.optional(MapId.STREAM_CODEC).decode(buf);
                int mapScale = buf.readVarInt();
                int mapCenterX = buf.readInt();
                int mapCenterZ = buf.readInt();
                int claimSize = buf.readVarInt();
                List<ChunkPos> claims = new ArrayList<>(claimSize);
                for (int i = 0; i < claimSize; i++) {
                    claims.add(new ChunkPos(buf.readLong()));
                }
                int claimCount = buf.readVarInt();
                boolean inviteOnly = buf.readBoolean();
                boolean friendlyFire = buf.readBoolean();
                KingdomRank viewerRank = KingdomRank.values()[buf.readVarInt()];
                int count = buf.readVarInt();
                List<Member> members = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    members.add(Member.STREAM_CODEC.decode(buf));
                }
                return new KingdomViewPayload(townHall, name, color, banner, map, mapScale, mapCenterX, mapCenterZ,
                        claims, claimCount, inviteOnly, friendlyFire, viewerRank, members);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
