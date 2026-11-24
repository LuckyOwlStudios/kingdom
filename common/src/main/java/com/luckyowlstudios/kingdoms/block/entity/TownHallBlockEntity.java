package com.luckyowlstudios.kingdoms.block.entity;

import com.luckyowlstudios.kingdoms.menu.TownHallMenu;
import com.luckyowlstudios.kingdoms.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the state of a kingdom. Empty for now; kingdom data will be saved here later.
 */
public class TownHallBlockEntity extends BlockEntity implements MenuProvider {

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.kingdoms.town_hall");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TownHallMenu(containerId, playerInventory, ContainerLevelAccess.create(this.level, this.worldPosition));
    }
}
