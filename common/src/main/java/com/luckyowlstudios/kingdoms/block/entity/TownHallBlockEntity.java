package com.luckyowlstudios.kingdoms.block.entity;

import com.luckyowlstudios.kingdoms.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Town Hall's block entity. Empty for now: kingdoms are saved in the world's {@code data/kingdoms.dat}.
 */
public class TownHallBlockEntity extends BlockEntity {

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL.get(), pos, state);
    }
}
