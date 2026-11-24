package com.luckyowlstudios.kingdoms.menu;

import com.luckyowlstudios.kingdoms.entity.GuideEntity;
import com.luckyowlstudios.kingdoms.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder menu opened by talking to a guide. The real UI will be designed later.
 */
public class GuideMenu extends AbstractContainerMenu {

    /** The guide being talked to. Only known on the server. */
    @Nullable
    private final GuideEntity guide;

    /** Client-side constructor. */
    public GuideMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null);
    }

    public GuideMenu(int containerId, Inventory playerInventory, @Nullable GuideEntity guide) {
        super(ModMenus.GUIDE.get(), containerId);
        this.guide = guide;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.guide == null || (this.guide.isAlive() && player.distanceToSqr(this.guide) <= 64.0D);
    }
}
