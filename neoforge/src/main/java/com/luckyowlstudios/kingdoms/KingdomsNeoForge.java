package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.platform.NeoForgeRegistryHelper;
import com.luckyowlstudios.kingdoms.registry.ModEntities;
import com.luckyowlstudios.kingdoms.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(Constants.MOD_ID)
public class KingdomsNeoForge {

    public KingdomsNeoForge(IEventBus eventBus) {
        NeoForgeRegistryHelper.setModBus(eventBus);
        CommonClass.init();

        eventBus.addListener(KingdomsNeoForge::onEntityAttributes);
        eventBus.addListener(KingdomsNeoForge::onBuildCreativeTabs);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUIDE.get(), GuideEntity.createAttributes().build());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.TOWN_HALL.get());
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GUIDE_SPAWN_EGG.get());
        }
    }
}
