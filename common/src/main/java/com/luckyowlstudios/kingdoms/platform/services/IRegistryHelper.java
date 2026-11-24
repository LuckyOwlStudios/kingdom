package com.luckyowlstudios.kingdoms.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Loader-specific registration. Fabric registers immediately, NeoForge defers to its registry events,
 * so always read registered objects through the returned supplier.
 */
public interface IRegistryHelper {

    <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory);

    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... validBlocks);

    <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory);

    /**
     * Registers a point of interest made of every state of {@code block}. Each loader also has to tell the
     * game which block states belong to it, so this can't go through {@link #register}.
     */
    Supplier<PoiType> poiType(String name, int maxTickets, int validRange, Supplier<? extends Block> block);

    Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties);

    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory);
    }
}
