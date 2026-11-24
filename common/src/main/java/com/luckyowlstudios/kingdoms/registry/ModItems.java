package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModItems {

    public static final Supplier<BlockItem> TOWN_HALL = Services.REGISTRY.register(BuiltInRegistries.ITEM, "town_hall",
            () -> new BlockItem(ModBlocks.TOWN_HALL.get(), new Item.Properties()));

    public static final Supplier<BlockItem> WATCHTOWER = Services.REGISTRY.register(BuiltInRegistries.ITEM, "watchtower",
            () -> new BlockItem(ModBlocks.WATCHTOWER.get(), new Item.Properties()));

    public static final Supplier<Item> GUIDE_SPAWN_EGG = Services.REGISTRY.register(BuiltInRegistries.ITEM, "guide_spawn_egg",
            () -> Services.REGISTRY.spawnEgg(ModEntities.GUIDE, 0x8C1C1C, 0xD9A93A, new Item.Properties()));

    private ModItems() {
    }

    static void init() {
    }
}
