package com.luckyowlstudios.kingdoms.platform;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.platform.services.IRegistryHelper;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        T value = Registry.register(registry, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name), factory.get());
        return () -> value;
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        return FabricBlockEntityTypeBuilder.create(factory::create, validBlocks).build();
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
        return new MenuType<>(factory::create, FeatureFlags.VANILLA_SET);
    }

    @Override
    public Supplier<PoiType> poiType(String name, int maxTickets, int validRange, Supplier<? extends Block> block) {
        // Fabric's helper also tells the game which block states belong to the point of interest.
        PoiType value = PointOfInterestHelper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name), maxTickets, validRange, block.get());
        return () -> value;
    }

    @Override
    public Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties) {
        return new SpawnEggItem(type.get(), backgroundColor, highlightColor, properties);
    }
}
