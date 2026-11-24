package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kingdom XP: whenever a member gains experience points, in any way, their kingdom gains the same amount. Enough XP
 * raises the kingdom a level, and each level takes more than the last (see {@link KingdomLevel#LEVELS}).
 * XP handed out by the Shared XP perk isn't counted again. Server side only.
 */
public final class KingdomXp {

    private KingdomXp() {
    }

    /** Called after a player gains experience points. */
    public static void onExperienceGained(ServerPlayer player, int amount) {
        if (amount <= 0 || SharedXp.isSharing()) {
            return;
        }
        Kingdoms.ofPlayer(player).ifPresent(kingdom -> add(player.server, kingdom, amount));
    }

    /** Gives a kingdom XP, levelling it up (with a celebration) if that's enough. */
    public static void add(MinecraftServer server, Kingdom kingdom, int amount) {
        if (amount <= 0) {
            return;
        }
        int before = kingdom.level();
        KingdomData.get(server).addXp(kingdom, amount);
        Kingdoms.onLevelChanged(server, kingdom, before);
    }
}
