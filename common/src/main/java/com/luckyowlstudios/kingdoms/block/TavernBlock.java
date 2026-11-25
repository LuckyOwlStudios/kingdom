package com.luckyowlstudios.kingdoms.block;

import com.luckyowlstudios.kingdoms.kingdom.KingdomTavern;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Tavern: the Innkeeper villager's job site. A kingdom with an Innkeeper at a Tavern on its land gets a second
 * villager move-in each night (see {@link KingdomTavern}). Faces the player when placed. Right-clicking it says
 * whether it's drawing travellers in.
 */
public class TavernBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TavernBlock> CODEC = simpleCodec(TavernBlock::new);

    public TavernBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            KingdomTavern.onTavernUsed(serverPlayer, pos);
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
