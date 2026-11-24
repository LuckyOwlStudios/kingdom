package com.luckyowlstudios.kingdoms.registry;

import com.luckyowlstudios.kingdoms.menu.GuideMenu;
import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public final class ModMenus {

    public static final Supplier<MenuType<GuideMenu>> GUIDE = Services.REGISTRY.register(BuiltInRegistries.MENU, "guide",
            () -> Services.REGISTRY.menuType(GuideMenu::new));

    private ModMenus() {
    }

    static void init() {
    }
}
