package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.block.TownHallBlock;
import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public final class ModBlocks {

    public static final Supplier<TownHallBlock> TOWN_HALL = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "town_hall",
            () -> new TownHallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    private ModBlocks() {
    }

    static void init() {
    }
}
