package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

public final class ModEntities {

    public static final Supplier<EntityType<GuideEntity>> GUIDE = Services.REGISTRY.register(BuiltInRegistries.ENTITY_TYPE, "guide",
            () -> EntityType.Builder.<GuideEntity>of(GuideEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("guide"));

    private ModEntities() {
    }

    static void init() {
    }
}
