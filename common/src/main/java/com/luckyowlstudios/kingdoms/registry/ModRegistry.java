package com.luckyowlstudios.kingdoms.registry;

/**
 * Loads every registry class in a fixed order. Entity types come before items because
 * Fabric builds the spawn egg straight away and needs the entity type to exist.
 */
public final class ModRegistry {

    private ModRegistry() {
    }

    public static void init() {
        ModBlocks.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModItems.init();
        ModMenus.init();
        ModVillagers.init();
    }
}
