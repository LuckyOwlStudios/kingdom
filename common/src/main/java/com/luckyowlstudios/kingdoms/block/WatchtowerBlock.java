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
}
