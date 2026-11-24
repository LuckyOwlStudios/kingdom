package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final Supplier<BlockEntityType<TownHallBlockEntity>> TOWN_HALL = Services.REGISTRY.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "town_hall",
            () -> Services.REGISTRY.blockEntityType(TownHallBlockEntity::new, ModBlocks.TOWN_HALL.get()));

    private ModBlockEntities() {
    }

    static void init() {
    }
}
