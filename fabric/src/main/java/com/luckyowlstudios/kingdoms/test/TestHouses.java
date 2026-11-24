package com.luckyowlstudios.kingdoms.test;

import com.luckyowlstudios.kingdoms.kingdom.House;
import com.luckyowlstudios.kingdoms.kingdom.HouseScan;
import com.luckyowlstudios.kingdoms.kingdom.KingdomHouses;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Builds houses for game tests: villagers only move in where there's a free bed in a house (see {@link KingdomHouses}).
 */
final class TestHouses {

    private TestHouses() {
    }

    /**
     * Builds a stone brick house from {@code corner}: 5 blocks each way outside, 3 inside, with a door in the east wall,
     * one or two beds along the north and south walls, a glowstone light in the ceiling (if {@code lit}) and a House
     * Marker in the south-east corner.
     *
     * @return where the House Marker is
     */
    static BlockPos build(ServerLevel level, BlockPos corner, int beds, boolean lit) {
        box(level, corner, 5, 5, 5);
        if (lit) {
            level.setBlockAndUpdate(corner.offset(2, 4, 2), Blocks.GLOWSTONE.defaultBlockState());
        }
        door(level, corner.offset(4, 1, 2), Direction.EAST);
        bed(level, corner.offset(1, 1, 1), Direction.EAST);
        if (beds >= 2) {
            bed(level, corner.offset(1, 1, 3), Direction.EAST);
        }
        BlockPos marker = corner.offset(3, 1, 3);
        level.setBlockAndUpdate(marker, ModBlocks.HOUSE_MARKER.get().defaultBlockState());
        return marker;
    }

    /** A hollow stone brick box, walls, floor and ceiling included, {@code x} by {@code y} by {@code z} outside. */
    static void box(ServerLevel level, BlockPos corner, int x, int y, int z) {
        for (int dx = 0; dx < x; dx++) {
            for (int dy = 0; dy < y; dy++) {
                for (int dz = 0; dz < z; dz++) {
                    boolean wall = dx == 0 || dx == x - 1 || dy == 0 || dy == y - 1 || dz == 0 || dz == z - 1;
                    level.setBlockAndUpdate(corner.offset(dx, dy, dz),
                            wall ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** An oak door, bottom half at {@code lower}, walked through east-west or north-south depending on {@code facing}. */
    static void door(ServerLevel level, BlockPos lower, Direction facing) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        level.setBlockAndUpdate(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A red bed with its foot at {@code foot} and its head one block toward {@code facing}. */
    static void bed(ServerLevel level, BlockPos foot, Direction facing) {
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        level.setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(foot.relative(facing), bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** The house of the marker at {@code marker}. */
    static House house(ServerLevel level, BlockPos marker) {
        return KingdomHouses.at(level.getServer(), GlobalPos.of(level.dimension(), marker))
                .orElseThrow(() -> new GameTestAssertException("the House Marker at " + marker + " isn't known"));
    }

    /** Scans the house again, and fails (so a wait can try again next tick, once the light has spread) unless it's a home. */
    static void assertReady(ServerLevel level, BlockPos marker) {
        House house = house(level, marker);
        HouseScan.Result scan = KingdomHouses.rescan(level, house);
        if (!house.valid()) {
            throw new GameTestAssertException("the house at " + marker + " isn't a home: "
                    + KingdomHouses.problems(level.getServer(), house, scan).stream().map(Component::getString).toList());
        }
    }
}
