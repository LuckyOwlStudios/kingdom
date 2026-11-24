package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * The Tax Collector perk: each morning, every villager living in the kingdom pays emeralds into the Town Hall's
 * treasury, which officers and the leader empty from the Perks tab.
 * <ul>
 *     <li>Tier 1: 1 emerald per villager. The treasury holds {@link #DAYS_HELD} days of taxes (at least a stack),
 *     so it needs emptying.</li>
 *     <li>Tier 2: villagers with a profession pay 2.</li>
 *     <li>Tier 3: experienced villagers (Journeyman and up) pay 3, and the treasury holds twice as much.</li>
 * </ul>
 * Villagers in unloaded chunks can't be looked at, so they pay the basic rate. Server side only.
 */
public final class TaxCollector {

    /** How many days of taxes the treasury holds before it's full. */
    public static final int DAYS_HELD = 7;

    private TaxCollector() {
    }

    /** What one villager pays each morning at this tier, or 1 if the villager can't be looked at. */
    public static int rate(Villager villager, int tier) {
        if (villager == null || tier < 2) {
            return 1;
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return 1;
        }
        // Villager levels: 1 Novice, 2 Apprentice, 3 Journeyman, 4 Expert, 5 Master.
        return tier >= 3 && villager.getVillagerData().getLevel() >= 3 ? 3 : 2;
    }

    /** Emeralds the kingdom's villagers pay in a day, or 0 without the perk. */
    public static int dailyTaxes(MinecraftServer server, Kingdom kingdom) {
        int tier = KingdomPerks.activeTier(kingdom, KingdomPerks.TAX_COLLECTOR);
        if (tier == 0) {
            return 0;
        }
        int total = 0;
        for (UUID resident : kingdom.residents()) {
            total += rate(find(server, resident), tier);
        }
        return total;
    }

    /** How many emeralds the treasury holds, or 0 without the perk. */
    public static int treasuryCap(MinecraftServer server, Kingdom kingdom) {
        int tier = KingdomPerks.activeTier(kingdom, KingdomPerks.TAX_COLLECTOR);
        if (tier == 0) {
            return 0;
        }
        // At least a stack's worth of room, so a kingdom with few villagers still has a useful treasury.
        int days = tier >= 3 ? DAYS_HELD * 2 : DAYS_HELD;
        return Math.max(64, days * dailyTaxes(server, kingdom));
    }

    /** A new morning: the villagers pay up, as far as the treasury has room. */
    public static void morning(MinecraftServer server, Kingdom kingdom) {
        int daily = dailyTaxes(server, kingdom);
        if (daily <= 0) {
            return;
        }
        int cap = treasuryCap(server, kingdom);
        int taxes = Math.max(kingdom.taxes(), Math.min(cap, kingdom.taxes() + daily));
        if (taxes != kingdom.taxes()) {
            KingdomData.get(server).setTaxes(kingdom, taxes);
        }
    }

    /** Officers and the leader empty the treasury into their inventory; whatever doesn't fit drops at their feet. */
    public static boolean collect(ServerPlayer player) {
        Kingdom kingdom = Kingdoms.ofPlayer(player).orElse(null);
        if (kingdom == null) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_in_kingdom"));
            return false;
        }
        if (!kingdom.rank(player.getUUID()).atLeast(KingdomRank.OFFICER)) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_officer", kingdom.displayName()));
            return false;
        }
        int taxes = kingdom.taxes();
        if (taxes <= 0) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.tax_collector.empty"));
            return false;
        }
        KingdomData.get(player.server).setTaxes(kingdom, 0);
        int left = taxes;
        while (left > 0) {
            int count = Math.min(left, 64);
            left -= count;
            ItemStack stack = new ItemStack(Items.EMERALD, count);
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("kingdoms.perk.tax_collector.collected", taxes));
        return true;
    }

    private static Villager find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }
}
