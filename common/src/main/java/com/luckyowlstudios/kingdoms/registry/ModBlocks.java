package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.block.ScholarsDeskBlock;
import com.luckyowlstudios.kingdoms.block.TownHallBlock;
import com.luckyowlstudios.kingdoms.block.WatchtowerBlock;
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

    /** The Innkeeper's job site. See {@link com.luckyowlstudios.kingdoms.kingdom.KingdomTavern}. */
    public static final Supplier<com.luckyowlstudios.kingdoms.block.TavernBlock> TAVERN = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "tavern",
            () -> new com.luckyowlstudios.kingdoms.block.TavernBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    public static final Supplier<ScholarsDeskBlock> SCHOLARS_DESK = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "scholars_desk",
            () -> new ScholarsDeskBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    public static final Supplier<WatchtowerBlock> WATCHTOWER = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "watchtower",
            () -> new WatchtowerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    /** Marks a house as a home for villagers. See {@link com.luckyowlstudios.kingdoms.kingdom.KingdomHouses}. */
    public static final Supplier<com.luckyowlstudios.kingdoms.block.HouseMarkerBlock> HOUSE_MARKER = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "house_marker",
            () -> new com.luckyowlstudios.kingdoms.block.HouseMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .ignitedByLava()));

    private ModBlocks() {
    }

    static void init() {
    }
}
