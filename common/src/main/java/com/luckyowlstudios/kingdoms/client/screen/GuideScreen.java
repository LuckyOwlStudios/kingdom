package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.menu.GuideMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Placeholder guide UI. The real one will be designed later. */
public class GuideScreen extends PlaceholderScreen<GuideMenu> {

    public GuideScreen(GuideMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
