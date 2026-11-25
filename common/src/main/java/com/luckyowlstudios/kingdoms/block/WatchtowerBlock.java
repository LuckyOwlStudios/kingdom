package com.luckyowlstudios.kingdoms.block;

import com.luckyowlstudios.kingdoms.kingdom.Watchtowers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Watchtower: the Lookout villager's job site. Build it at the top of a tower on your kingdom's land and the
 * Lookout working there warns the kingdom about what it sees (see {@link Watchtowers}). Right-clicking one says
 * whether a Lookout is keeping watch from it.
 */
public class WatchtowerBlock extends Block {

    public static final MapCodec<WatchtowerBlock> CODEC = simpleCodec(WatchtowerBlock::new);

    public WatchtowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Watchtowers.onTowerUsed(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Placed by a kingdom member: it belongs to their kingdom (see KingdomWorkstations). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        com.luckyowlstudios.kingdoms.kingdom.KingdomWorkstations.onPlaced(level, pos, state, placer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        com.luckyowlstudios.kingdoms.kingdom.KingdomWorkstations.onRemoved(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
