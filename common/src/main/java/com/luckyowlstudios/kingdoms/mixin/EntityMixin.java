package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomVillagers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /** A kingdom's villager dying (or turning into a zombie or witch) frees its place for the next arrival. */
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void kingdoms$onRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        KingdomVillagers.onRemoved((Entity) (Object) this, reason);
    }
}
