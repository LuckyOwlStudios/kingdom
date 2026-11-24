package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerkEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    /** Watchtowers: fewer hostile mobs spawn naturally on a kingdom's land. */
    @Inject(method = "isValidPositionForMob", at = @At("RETURN"), cancellable = true)
    private static void kingdoms$watchtowers(ServerLevel level, Mob mob, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KingdomPerkEffects.watchtowerBlocksSpawn(level, mob)) {
            cir.setReturnValue(false);
        }
    }
}
