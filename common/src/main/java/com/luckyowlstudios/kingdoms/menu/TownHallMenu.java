package com.luckyowlstudios.kingdoms.menu;

import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Placeholder town hall menu. It has no slots yet.
 */
public class TownHallMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;

    /** Client-side constructor. */
    public TownHallMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public TownHallMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenus.TOWN_HALL.get(), containerId);
        this.access = access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.TOWN_HALL.get());
    }
}
