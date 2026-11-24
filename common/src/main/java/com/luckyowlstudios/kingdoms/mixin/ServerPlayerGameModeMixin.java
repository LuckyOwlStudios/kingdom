package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerkEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    /** Fortified Walls: outsiders can't break blocks on a kingdom's land. The client gets the block back. */
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void kingdoms$fortifiedWallsBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (KingdomPerkEffects.wallsStop(this.player, pos, KingdomPerkEffects.WALLS_BREAKING)) {
            cir.setReturnValue(false);
        }
    }

    /** Fortified Walls tier 3: outsiders can't place blocks or use doors, chests and the like on a kingdom's land. */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void kingdoms$fortifiedWallsUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = hit.getBlockPos();
        if (KingdomPerkEffects.wallsStop(player, pos, KingdomPerkEffects.WALLS_USING)
                || KingdomPerkEffects.wallsStop(player, pos.relative(hit.getDirection()), KingdomPerkEffects.WALLS_USING)) {
            // Vanilla sends both blocks back to the client after this, so nothing appears to have changed.
            player.inventoryMenu.sendAllDataToRemote();
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
