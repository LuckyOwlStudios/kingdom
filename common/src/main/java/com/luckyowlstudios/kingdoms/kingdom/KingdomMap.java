package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.List;
import java.util.Optional;

/**
 * A kingdom's map: a square grid of vanilla maps, all at full detail (one block per pixel), with the middle one
 * centred exactly on the Town Hall. Growing the map adds a ring of maps around the edge instead of zooming out,
 * so it shows more land at the same detail.
 *
 * @param radius how many maps the grid reaches out from the middle one; 0 is a single map, 1 is 3x3, and so on
 * @param tiles  the maps made so far
 * @param legacy a single map from before maps were grids, only read from old saves
 */
public record KingdomMap(int radius, List<Tile> tiles, Optional<MapId> legacy) {

    public static final KingdomMap EMPTY = new KingdomMap(0, List.of(), Optional.empty());

    /** Blocks (and pixels) across one map. */
    public static final int TILE_SIZE = 128;

    /** One map of the grid, {@code x} maps east and {@code z} maps south of the middle one. */
    public record Tile(int x, int z, MapId id) {
        public static final Codec<Tile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Tile::x),
                Codec.INT.fieldOf("z").forGetter(Tile::z),
                MapId.CODEC.fieldOf("id").forGetter(Tile::id)
        ).apply(instance, Tile::new));
    }

    private record Grid(int radius, List<Tile> tiles) {
        static final Codec<Grid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("radius", 0).forGetter(Grid::radius),
                Tile.CODEC.listOf().optionalFieldOf("tiles", List.of()).forGetter(Grid::tiles)
        ).apply(instance, Grid::new));
    }

    /** Saved as {@code map_grid}; an old save's single {@code map} is read so it can be upgraded. */
    public static final MapCodec<KingdomMap> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Grid.CODEC.optionalFieldOf("map_grid").forGetter(map -> map.tiles.isEmpty() && map.radius == 0
                    ? Optional.empty() : Optional.of(new Grid(map.radius, map.tiles))),
            MapId.CODEC.optionalFieldOf("map").forGetter(KingdomMap::legacy)
    ).apply(instance, (grid, legacy) -> new KingdomMap(grid.map(Grid::radius).orElse(0),
            grid.map(Grid::tiles).orElse(List.of()), legacy)));

    public Optional<MapId> tile(int x, int z) {
        return this.tiles.stream().filter(tile -> tile.x == x && tile.z == z).map(Tile::id).findFirst();
    }

    /** Maps across the whole grid. */
    public int width() {
        return this.radius * 2 + 1;
    }
}
