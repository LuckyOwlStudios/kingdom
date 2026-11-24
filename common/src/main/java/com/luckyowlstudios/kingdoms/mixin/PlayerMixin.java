package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomXp;
import com.luckyowlstudios.kingdoms.kingdom.SharedXp;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * Experience a member gains also counts toward their kingdom's level, and (with the Shared XP perk) is shared with
     * the rest of the kingdom.
     */
    @Inject(method = "giveExperiencePoints", at = @At("TAIL"))
    private void kingdoms$shareExperience(int amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            KingdomXp.onExperienceGained(player, amount);
            SharedXp.onExperienceGained(player, amount);
        }
    }
}
