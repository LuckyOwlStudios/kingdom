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

import java.util.Optional;

/**
 * Each kingdom has a map centred on its Town Hall, like a vanilla map. Members fill it in as they walk around,
 * and giving the Town Hall an empty map zooms it out one step, up to vanilla's largest scale.
 */
public final class KingdomMaps {

    public static final int MAX_SCALE = 4;

    private KingdomMaps() {
    }

    private static Optional<ServerLevel> hallLevel(ServerPlayer player, Kingdom kingdom) {
        return kingdom.townHall().map(hall -> player.server.getLevel(hall.dimension()));
    }

    /** The kingdom's map data, made at the smallest scale the first time it's needed. */
    public static Optional<MapItemSavedData> getOrCreate(ServerPlayer player, Kingdom kingdom) {
        Optional<ServerLevel> level = hallLevel(player, kingdom);
        if (level.isEmpty() || kingdom.townHall().isEmpty()) {
            return Optional.empty();
        }
        if (kingdom.map().isPresent()) {
            MapItemSavedData data = level.get().getMapData(kingdom.map().get());
            GlobalPos hall = kingdom.townHall().get();
            if (data != null && data.centerX == hall.pos().getX() && data.centerZ == hall.pos().getZ()) {
                return Optional.of(data);
            }
            if (data != null) {
                // Made before maps were centred on the Town Hall: start again at the same size, centred properly.
                create(player, kingdom, level.get(), hall, data.scale);
                return kingdom.map().map(id -> level.get().getMapData(id));
            }
        }
        create(player, kingdom, level.get(), kingdom.townHall().get(), (byte) 0);
        return kingdom.map().map(id -> level.get().getMapData(id));
    }

    private static void create(ServerPlayer player, Kingdom kingdom, ServerLevel level, GlobalPos hall, byte scale) {
        // Not MapItem.create: vanilla snaps a map's centre to a world-wide grid, which would leave the Town Hall
        // off in a corner and make the map grow lopsided. Kingdom maps are centred exactly on the hall instead.
        MapItemSavedData data = MapItemSavedDataAccessor.kingdoms$create(hall.pos().getX(), hall.pos().getZ(), scale,
                true, false, false, level.dimension());
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        KingdomData.get(player.server).setMap(kingdom, id);
    }

    public static int scale(ServerPlayer player, Kingdom kingdom) {
        return getOrCreate(player, kingdom).map(data -> (int) data.scale).orElse(0);
    }

    /** A member handed the Town Hall an empty map. Uses it up and zooms the kingdom map out one step. */
    public static boolean grow(ServerPlayer player, Kingdom kingdom, ItemStack emptyMap) {
        if (!emptyMap.is(Items.MAP) || kingdom.townHall().isEmpty()) {
            return false;
        }
        Optional<MapItemSavedData> data = getOrCreate(player, kingdom);
        Optional<ServerLevel> level = hallLevel(player, kingdom);
        if (data.isEmpty() || level.isEmpty()) {
            return false;
        }
        if (data.get().scale >= MAX_SCALE) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.map.max"), true);
            return true;
        }
        int newScale = data.get().scale + 1;
        create(player, kingdom, level.get(), kingdom.townHall().get(), (byte) newScale);
        emptyMap.consume(1, player);
        player.displayClientMessage(Component.translatable("kingdoms.kingdom.map.grown", newScale, MAX_SCALE), true);
        return true;
    }

    /** Fills in the map around a member, the same way holding a vanilla map does. Called about once a second. */
    public static void explore(ServerPlayer player) {
        Kingdoms.ofPlayer(player).ifPresent(kingdom -> kingdom.townHall()
                .filter(hall -> hall.dimension() == player.level().dimension())
                .flatMap(hall -> getOrCreate(player, kingdom))
                .ifPresent(data -> ((MapItem) Items.FILLED_MAP).update(player.level(), player, data)));
    }

    /** Sends the map's pixels to a player about to look at it in the Town Hall screen. */
    public static void sendTo(ServerPlayer player, Kingdom kingdom) {
        kingdom.map().ifPresent(id -> getOrCreate(player, kingdom).ifPresent(data -> {
            data.getHoldingPlayer(player);
            Packet<?> packet = data.getUpdatePacket(id, player);
            if (packet != null) {
                player.connection.send(packet);
            }
        }));
    }
}
