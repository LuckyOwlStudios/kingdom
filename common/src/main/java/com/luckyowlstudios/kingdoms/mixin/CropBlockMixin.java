package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerkEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CropBlock.class)
public abstract class CropBlockMixin {

    /** Bountiful Harvest: crops on a kingdom's land sometimes grow an extra stage. */
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void kingdoms$bountifulHarvest(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CropBlock self = (CropBlock) (Object) this;
        BlockState now = level.getBlockState(pos);
        if (now.getBlock() != self || self.isMaxAge(now) || level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        float chance = KingdomPerkEffects.bountifulHarvestChance(level, pos);
        if (chance > 0 && random.nextFloat() < chance) {
            level.setBlock(pos, self.getStateForAge(self.getAge(now) + 1), 2);
        }
    }
}
