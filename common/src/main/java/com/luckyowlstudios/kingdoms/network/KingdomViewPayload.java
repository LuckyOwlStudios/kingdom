package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.kingdom.KingdomMap;
import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server to client: everything the Town Hall screen shows about a kingdom. Sent when a member opens the
 * Town Hall, and again after each change they make so the screen stays current.
 *
 * @param viewerRank the rank of the player looking, which decides which buttons work
 * @param members    members and banned players; banned ones have rank {@link KingdomRank#NONE}
 * @param mapRadius  rings of maps around the middle one, which is centred on the Town Hall (see {@link KingdomMap})
 * @param mapTiles   the maps of the grid
 * @param claims     the kingdom's claimed chunks in the map's dimension, to draw on the map
 * @param claimCount how many chunks the kingdom holds in every dimension
 * @param growth     the kingdom's level and what it allows
 */
public record KingdomViewPayload(BlockPos townHall, String name, DyeColor color, ItemStack banner,
                                 int mapRadius, List<KingdomMap.Tile> mapTiles,
                                 List<ChunkPos> claims, int claimCount, boolean inviteOnly, boolean friendlyFire,
                                 KingdomRank viewerRank, List<Member> members, Growth growth) implements CustomPacketPayload {

    public static final Type<KingdomViewPayload> TYPE = new Type<>(Constants.id("kingdom_view"));

    public record Member(UUID id, String name, KingdomRank rank, boolean banned) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.VAR_INT.map(i -> KingdomRank.values()[i], KingdomRank::ordinal), Member::rank,
                ByteBufCodecs.BOOL, Member::banned,
                Member::new);
    }

    /**
     * @param villagers    villagers living in the kingdom
     * @param villagerCap  how many the level allows
     * @param landCap      how many chunks the level allows
     * @param xp           kingdom XP earned toward the next level
     * @param xpToNext     XP the next level takes, or 0 at the top level
     * @param perkPoints   perk points the kingdom has left to spend
     * @param perks        the tier the kingdom has of each perk it has unlocked
     * @param taxes        emeralds waiting in the Town Hall's treasury
     * @param taxCap       how many the treasury holds (0 without the Tax Collector perk)
     */
    public record Growth(int level, int maxLevel, int villagers, int villagerCap, int landCap, int xp, int xpToNext,
                         int perkPoints, Map<ResourceLocation, Integer> perks, int taxes, int taxCap) {
        private static final StreamCodec<io.netty.buffer.ByteBuf, Map<ResourceLocation, Integer>> PERKS_CODEC =
                ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT);

        /** The tier the kingdom has of a perk, or 0. */
        public int perkTier(ResourceLocation perk) {
            return this.perks.getOrDefault(perk, 0);
        }

        // More fields than StreamCodec.composite takes, so written out by hand.
        static final StreamCodec<RegistryFriendlyByteBuf, Growth> STREAM_CODEC = StreamCodec.of(
                (buf, growth) -> {
                    buf.writeVarInt(growth.level);
                    buf.writeVarInt(growth.maxLevel);
                    buf.writeVarInt(growth.villagers);
                    buf.writeVarInt(growth.villagerCap);
                    buf.writeVarInt(growth.landCap);
                    buf.writeVarInt(growth.xp);
                    buf.writeVarInt(growth.xpToNext);
                    buf.writeVarInt(growth.perkPoints);
                    PERKS_CODEC.encode(buf, growth.perks);
                    buf.writeVarInt(growth.taxes);
                    buf.writeVarInt(growth.taxCap);
                },
                buf -> new Growth(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), PERKS_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));

        public boolean isMax() {
            return this.level >= this.maxLevel;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, KingdomViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> {
                BlockPos.STREAM_CODEC.encode(buf, view.townHall);
                ByteBufCodecs.STRING_UTF8.encode(buf, view.name);
                DyeColor.STREAM_CODEC.encode(buf, view.color);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.banner);
                buf.writeVarInt(view.mapRadius);
                buf.writeVarInt(view.mapTiles.size());
                view.mapTiles.forEach(tile -> {
                    buf.writeVarInt(tile.x());
                    buf.writeVarInt(tile.z());
                    MapId.STREAM_CODEC.encode(buf, tile.id());
                });
                buf.writeVarInt(view.claims.size());
                view.claims.forEach(chunk -> buf.writeLong(chunk.toLong()));
                buf.writeVarInt(view.claimCount);
                buf.writeBoolean(view.inviteOnly);
                buf.writeBoolean(view.friendlyFire);
                buf.writeVarInt(view.viewerRank.ordinal());
                buf.writeVarInt(view.members.size());
                view.members.forEach(member -> Member.STREAM_CODEC.encode(buf, member));
                Growth.STREAM_CODEC.encode(buf, view.growth);
            },
            buf -> {
                BlockPos townHall = BlockPos.STREAM_CODEC.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                DyeColor color = DyeColor.STREAM_CODEC.decode(buf);
                ItemStack banner = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                int mapRadius = buf.readVarInt();
                int tileCount = buf.readVarInt();
                List<KingdomMap.Tile> mapTiles = new ArrayList<>(tileCount);
                for (int i = 0; i < tileCount; i++) {
                    mapTiles.add(new KingdomMap.Tile(buf.readVarInt(), buf.readVarInt(), MapId.STREAM_CODEC.decode(buf)));
                }
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
                Growth growth = Growth.STREAM_CODEC.decode(buf);
                return new KingdomViewPayload(townHall, name, color, banner, mapRadius, mapTiles,
                        claims, claimCount, inviteOnly, friendlyFire, viewerRank, members, growth);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
