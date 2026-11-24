package com.luckyowlstudios.kingdoms.platform;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.platform.services.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class NeoForgeRegistryHelper implements IRegistryHelper {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new HashMap<>();
    private static IEventBus modBus;

    /** Must be called from the mod constructor before {@link com.luckyowlstudios.kingdoms.CommonClass#init()}. */
    public static void setModBus(IEventBus bus) {
        modBus = bus;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        DeferredRegister<R> deferred = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry.key(), key -> {
            if (modBus == null) {
                throw new IllegalStateException("Registered " + name + " before the mod event bus was set");
            }
            DeferredRegister<R> created = DeferredRegister.create(registry.key(), Constants.MOD_ID);
            created.register(modBus);
            return created;
        });
        return deferred.register(name, factory);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        return BlockEntityType.Builder.of(factory::create, validBlocks).build(null);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
        return IMenuTypeExtension.create((containerId, playerInventory, buffer) -> factory.create(containerId, playerInventory));
    }

    @Override
    public Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties) {
        return new DeferredSpawnEggItem(type, backgroundColor, highlightColor, properties);
    }
}
