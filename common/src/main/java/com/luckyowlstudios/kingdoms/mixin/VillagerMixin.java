package com.luckyowlstudios.kingdoms.mixin;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerkEffects;
import com.luckyowlstudios.kingdoms.kingdom.KingdomVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {

    /** Trade Deals: villagers on a kingdom's land give its members a discount, like the Hero of the Village one. */
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void kingdoms$tradeDeals(Player player, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        for (MerchantOffer offer : self.getOffers()) {
            int discount = KingdomPerkEffects.tradeDiscount(level, self.blockPosition(), player, offer.getBaseCostA().getCount());
            if (discount > 0) {
                offer.addToSpecialPriceDiff(-discount);
            }
        }
    }

    /** Every few seconds, a villager working at a Town Hall joins that hall's kingdom if it hasn't yet. */
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void kingdoms$joinTownHallKingdom(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (self.tickCount % 100 == 0) {
            KingdomVillagers.checkAdvisor(self);
        }
    }
}
