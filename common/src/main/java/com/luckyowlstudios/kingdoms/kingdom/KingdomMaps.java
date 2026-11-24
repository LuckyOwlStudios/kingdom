package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.mixin.MapItemSavedDataAccessor;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Each kingdom has a map around its Town Hall ({@link KingdomMap}): a grid of full-detail vanilla maps that members
 * fill in as they walk around. Giving the Town Hall an empty map adds a ring of maps around the edge, so the map
 * covers more land without losing detail, up to {@link #MAX_RADIUS} rings.
 */
public final class KingdomMaps {

    public static final int MAX_RADIUS = 4;
    /** How far from a member, in blocks, a vanilla map at full detail fills itself in. */
    private static final int EXPLORE_REACH = 128;

    private KingdomMaps() {
    }

    private static Optional<ServerLevel> hallLevel(ServerPlayer player, Kingdom kingdom) {
        return kingdom.townHall().map(hall -> player.server.getLevel(hall.dimension()));
    }

    /**
     * The kingdom's map, with every map of the grid made and centred on the current Town Hall. Made the first time
     * it's needed, and remade if the Town Hall moved.
     */
    public static Optional<KingdomMap> getOrCreate(ServerPlayer player, Kingdom kingdom) {
        Optional<ServerLevel> found = hallLevel(player, kingdom);
        if (found.isEmpty() || kingdom.townHall().isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = found.get();
        GlobalPos hall = kingdom.townHall().get();
        KingdomMap map = kingdom.map();
        int radius = map.radius();
        if (map.legacy().isPresent()) {
            // An old save's single map, which zoomed out to grow: keep its size as rings of full-detail maps.
            MapItemSavedData old = level.getMapData(map.legacy().get());
            radius = old == null ? 0 : Math.min(old.scale, MAX_RADIUS);
        }
        List<KingdomMap.Tile> tiles = new ArrayList<>();
        boolean changed = map.legacy().isPresent() || radius != map.radius();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                int centerX = hall.pos().getX() + x * KingdomMap.TILE_SIZE;
                int centerZ = hall.pos().getZ() + z * KingdomMap.TILE_SIZE;
                Optional<MapId> id = map.tile(x, z).filter(existing -> {
                    MapItemSavedData data = level.getMapData(existing);
                    return data != null && data.centerX == centerX && data.centerZ == centerZ && data.scale == 0;
                });
                if (id.isEmpty()) {
                    id = Optional.of(create(level, centerX, centerZ));
                    changed = true;
                }
                tiles.add(new KingdomMap.Tile(x, z, id.get()));
            }
        }
        if (changed || tiles.size() != map.tiles().size()) {
            map = new KingdomMap(radius, List.copyOf(tiles), Optional.empty());
            KingdomData.get(player.server).setMap(kingdom, map);
        }
        return Optional.of(map);
    }

    private static MapId create(ServerLevel level, int centerX, int centerZ) {
        // Not MapItem.create: vanilla snaps a map's centre to a world-wide grid, which would leave the Town Hall
        // off-centre. Kingdom maps are laid out exactly around the hall instead.
        MapItemSavedData data = MapItemSavedDataAccessor.kingdoms$create(centerX, centerZ, (byte) 0,
                true, false, false, level.dimension());
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        return id;
    }

    /** How many rings of maps surround the middle one. */
    public static int radius(ServerPlayer player, Kingdom kingdom) {
        return getOrCreate(player, kingdom).map(KingdomMap::radius).orElse(0);
    }

    /** A member handed the Town Hall an empty map. Uses it up and adds a ring of maps around the kingdom map. */
    public static boolean grow(ServerPlayer player, Kingdom kingdom, ItemStack emptyMap) {
        if (!emptyMap.is(Items.MAP) || kingdom.townHall().isEmpty()) {
            return false;
        }
        Optional<KingdomMap> map = getOrCreate(player, kingdom);
        if (map.isEmpty()) {
            return false;
        }
        if (map.get().radius() >= MAX_RADIUS) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.map.max"), true);
            return true;
        }
        int radius = map.get().radius() + 1;
        KingdomData.get(player.server).setMap(kingdom, new KingdomMap(radius, map.get().tiles(), Optional.empty()));
        getOrCreate(player, kingdom); // makes the new ring
        emptyMap.consume(1, player);
        player.displayClientMessage(Component.translatable("kingdoms.kingdom.map.grown", radius, MAX_RADIUS), true);
        return true;
    }

    /** Fills in the maps near a member, the same way holding a vanilla map does. Called about once a second. */
    public static void explore(ServerPlayer player) {
        Kingdoms.ofPlayer(player).ifPresent(kingdom -> kingdom.townHall()
                .filter(hall -> hall.dimension() == player.level().dimension())
                .ifPresent(hall -> getOrCreate(player, kingdom).ifPresent(map -> {
                    ServerLevel level = player.serverLevel();
                    for (KingdomMap.Tile tile : map.tiles()) {
                        MapItemSavedData data = level.getMapData(tile.id());
                        // Only the maps close enough for the member to fill in any of.
                        int reach = EXPLORE_REACH + KingdomMap.TILE_SIZE / 2;
                        if (data != null && Math.abs(player.getX() - data.centerX) < reach && Math.abs(player.getZ() - data.centerZ) < reach) {
                            ((MapItem) Items.FILLED_MAP).update(level, player, data);
                        }
                    }
                })));
    }

    /** Sends the maps' pixels to a player about to look at them in the Town Hall screen. */
    public static void sendTo(ServerPlayer player, Kingdom kingdom) {
        Optional<ServerLevel> level = hallLevel(player, kingdom);
        getOrCreate(player, kingdom).ifPresent(map -> level.ifPresent(found -> {
            for (KingdomMap.Tile tile : map.tiles()) {
                MapItemSavedData data = found.getMapData(tile.id());
                if (data == null) {
                    continue;
                }
                data.getHoldingPlayer(player);
                Packet<?> packet = data.getUpdatePacket(tile.id(), player);
                if (packet != null) {
                    player.connection.send(packet);
                }
            }
        }));
    }
}
