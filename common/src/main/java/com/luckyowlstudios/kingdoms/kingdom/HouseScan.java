package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out whether the space a House Marker stands in is a home. Starting at the marker, it fills the air around
 * it (every block a mob could be in: anything without a collision box, like air, torches, flowers or carpets' tops)
 * until it runs into walls. Doors are walls too, so each room is filled on its own; then for every door the house
 * touches, it fills what's on the other side. If that's closed in, it's another room and becomes part of the house;
 * if it gets out (to the open sky, or further than a house can reach), the door leads outside.
 * <p>
 * A home is closed in (no gap to the sky, and no bigger than {@link #MAX_SPACE} blocks of air within
 * {@link #MAX_REACH} blocks of the marker), has at least one bed and a door leading outside, and no spot inside dark
 * enough for monsters to spawn. Pure reading, nothing is changed; server side only.
 */
public final class HouseScan {

    /** The most blocks of air a house can hold, every room together. A 16 by 16 house with 8 high rooms just fits. */
    public static final int MAX_SPACE = 2048;
    /** How far from the marker, sideways, a house can reach. */
    public static final int MAX_REACH = 24;
    /** How far up or down from the marker a house can reach. */
    public static final int MAX_HEIGHT = 16;
    /** The most doors looked through in one scan, so a house of a hundred doors can't slow the server down. */
    private static final int MAX_DOORS = 16;

    /** How the space got out, if it did. */
    public enum Opening {
        /** Closed in. */
        NONE,
        /** Open to the sky (no roof over some spot). */
        SKY,
        /** Bigger than {@link #MAX_SPACE}, or reaching past {@link #MAX_REACH} / {@link #MAX_HEIGHT}. */
        TOO_BIG,
        /** It runs into chunks that aren't loaded, so it can't be checked. */
        UNLOADED
    }

    /**
     * What a scan found. Everything after {@code space} is only filled in when the house is closed in.
     *
     * @param opening       how the space gets out, or {@link Opening#NONE}
     * @param leak          for {@link Opening#SKY}, a spot inside with nothing above it
     * @param space         blocks of air inside
     * @param beds          the head end of every bed in the house, in a fixed order
     * @param exteriorDoors how many doors lead outside
     * @param darkSpots     spots inside where a monster could spawn, nearest the marker first
     * @param floor         spots inside a villager can stand on, nearest the marker first
     * @param markers       other House Markers in the same house
     */
    public record Result(Opening opening, @Nullable BlockPos leak, int space, List<BlockPos> beds, int exteriorDoors,
                         List<BlockPos> darkSpots, List<BlockPos> floor, List<BlockPos> markers) {

        public boolean enclosed() {
            return this.opening == Opening.NONE;
        }

        /** Closed in, with a bed, a way out and no dark spots. */
        public boolean valid() {
            return this.enclosed() && !this.beds.isEmpty() && this.exteriorDoors > 0 && this.darkSpots.isEmpty();
        }
    }

    private record Flood(Set<BlockPos> cells, Set<BlockPos> touching, Opening opening, @Nullable BlockPos leak) {
    }

    private HouseScan() {
    }

    /** Scans the house the marker at {@code marker} stands in. */
    public static Result scan(ServerLevel world, BlockPos marker) {
        Flood first = flood(world, marker, marker, Set.of(), MAX_SPACE);
        if (first.opening() != Opening.NONE) {
            return new Result(first.opening(), first.leak(), first.cells().size(), List.of(), 0, List.of(), List.of(), List.of());
        }
        Set<BlockPos> cells = new HashSet<>(first.cells());
        Set<BlockPos> touching = new HashSet<>(first.touching());

        // Look through every door: into another room (part of the house), or outside (a way in and out).
        Set<BlockPos> doors = new HashSet<>();
        int exteriorDoors = 0;
        boolean grew = true;
        while (grew && doors.size() < MAX_DOORS) {
            grew = false;
            for (BlockPos pos : List.copyOf(touching)) {
                BlockState state = world.getBlockState(pos);
                if (!state.is(BlockTags.DOORS) || !(state.getBlock() instanceof DoorBlock)) {
                    continue;
                }
                BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
                if (doors.size() >= MAX_DOORS || !doors.add(lower)) {
                    continue;
                }
                // A door is walked through along the way it faces.
                Direction facing = state.getValue(DoorBlock.FACING);
                boolean frontInside = inside(cells, lower.relative(facing));
                boolean backInside = inside(cells, lower.relative(facing.getOpposite()));
                if (frontInside == backInside) {
                    continue; // a door between two rooms already in the house, or one the house only brushes past
                }
                BlockPos start = firstOpen(world, lower.relative(frontInside ? facing.getOpposite() : facing));
                if (start == null || cells.contains(start)) {
                    continue; // opens onto a wall
                }
                Flood beyond = flood(world, start, marker, cells, MAX_SPACE - cells.size());
                if (beyond.opening() != Opening.NONE) {
                    exteriorDoors++;
                } else {
                    cells.addAll(beyond.cells());
                    touching.addAll(beyond.touching());
                    grew = true;
                }
            }
        }

        // Beds and other markers the house touches.
        Set<BlockPos> beds = new LinkedHashSet<>();
        List<BlockPos> markers = new ArrayList<>();
        for (BlockPos pos : touching) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock) {
                beds.add(state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING)));
            } else if (!pos.equals(marker) && state.is(ModBlocks.HOUSE_MARKER.get())) {
                markers.add(pos);
            }
        }

        // Where a mob could stand: open, with room above, on top of something. Dark enough there, and a monster could spawn.
        int lightLimit = world.dimensionType().monsterSpawnBlockLightLimit();
        List<BlockPos> dark = new ArrayList<>();
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos pos : cells) {
            if (pos.equals(marker) || !isOpen(world, pos) || !world.getFluidState(pos).isEmpty() || !isOpen(world, pos.above())) {
                continue;
            }
            BlockPos below = pos.below();
            BlockState ground = world.getBlockState(below);
            if (ground.isFaceSturdy(world, below, Direction.UP)) {
                floor.add(pos);
            }
            if (ground.isValidSpawn(world, below, EntityType.ZOMBIE) && world.getBrightness(LightLayer.BLOCK, pos) <= lightLimit) {
                dark.add(pos);
            }
        }
        Comparator<BlockPos> nearest = Comparator.comparingDouble(pos -> pos.distSqr(marker));
        dark.sort(nearest.thenComparingLong(BlockPos::asLong));
        floor.sort(nearest.thenComparingLong(BlockPos::asLong));
        List<BlockPos> bedList = new ArrayList<>(beds);
        bedList.sort(Comparator.comparingLong(BlockPos::asLong));
        return new Result(Opening.NONE, null, cells.size(), List.copyOf(bedList), exteriorDoors, List.copyOf(dark),
                List.copyOf(floor), List.copyOf(markers));
    }

    /**
     * Fills the open space from {@code start}, never going into {@code exclude}, and stops as soon as it gets out.
     * {@code touching} is every solid block next to the space.
     */
    private static Flood flood(ServerLevel world, BlockPos start, BlockPos marker, Set<BlockPos> exclude, int budget) {
        Set<BlockPos> cells = new HashSet<>();
        Set<BlockPos> touching = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cells.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!world.isLoaded(pos)) {
                return new Flood(cells, touching, Opening.UNLOADED, pos);
            }
            if (Math.abs(pos.getX() - marker.getX()) > MAX_REACH || Math.abs(pos.getZ() - marker.getZ()) > MAX_REACH
                    || Math.abs(pos.getY() - marker.getY()) > MAX_HEIGHT) {
                return new Flood(cells, touching, Opening.TOO_BIG, pos);
            }
            // Nothing above that stops a mob (or the rain): this spot is out in the open.
            if (pos.getY() >= world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
                return new Flood(cells, touching, Opening.SKY, pos);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (cells.contains(next) || exclude.contains(next)) {
                    continue;
                }
                if (world.isOutsideBuildHeight(next)) {
                    return new Flood(cells, touching, Opening.TOO_BIG, pos);
                }
                // Never load or generate chunks just to look at a house.
                if (!world.isLoaded(next)) {
                    return new Flood(cells, touching, Opening.UNLOADED, next);
                }
                if (!isOpen(world, next)) {
                    touching.add(next);
                    continue;
                }
                if (cells.size() >= budget) {
                    return new Flood(cells, touching, Opening.TOO_BIG, next);
                }
                cells.add(next.immutable());
                queue.add(next.immutable());
            }
        }
        return new Flood(cells, touching, Opening.NONE, null);
    }

    /** True for spots a mob can be in: no collision box (air, torches, flowers, open fence gates, water...). */
    private static boolean isOpen(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /** True if the house has the spot beside a door, or the one above it (beside the door's top half). */
    private static boolean inside(Set<BlockPos> cells, BlockPos side) {
        return cells.contains(side) || cells.contains(side.above());
    }

    /** The open spot just past a door: beside its bottom half, or its top half. Null if both are blocked. */
    @Nullable
    private static BlockPos firstOpen(ServerLevel world, BlockPos side) {
        if (!world.isLoaded(side)) {
            return side; // the flood from here reports it as unloaded, so the door counts as leading out
        }
        if (isOpen(world, side)) {
            return side;
        }
        BlockPos above = side.above();
        return world.isLoaded(above) && isOpen(world, above) ? above : null;
    }
}
