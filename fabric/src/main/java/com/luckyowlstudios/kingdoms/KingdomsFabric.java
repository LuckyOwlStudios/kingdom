package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.item.CreativeModeTabs;

public class KingdomsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        FabricDefaultAttributeRegistry.register(ModEntities.GUIDE.get(), GuideEntity.createAttributes());

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(ModItems.TOWN_HALL.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(ModItems.GUIDE_SPAWN_EGG.get()));
    }
}
