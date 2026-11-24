package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.menu.TownHallMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Placeholder town hall UI. */
public class TownHallScreen extends PlaceholderScreen<TownHallMenu> {

    public TownHallScreen(TownHallMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
