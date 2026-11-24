package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Shared XP perk: when a kingdom member gains experience, every other member who is online gets a share.
 * <p>
 * In a kingdom of two, the other member gets 1/7 of it at tier 1, 1/5 at tier 2 and 1/4 at tier 3 (see
 * {@link #DIVISORS}). Each extra member makes every share smaller: with divisor {@code d}, a kingdom of {@code n}
 * gives each of the others {@code amount / (n + d - 2)}. All shares together stay below the XP that was gained,
 * however big the kingdom gets (tier 1 with 2 members: 14% in total, 5: 40%, 10: 60%).
 * <p>
 * Shares are often fractions of a point. What's left over is kept for each player (until the server stops) and
 * paid out once it adds up to a whole point. XP that comes from a share is never shared again.
 */
public final class SharedXp {

    /** For each tier: a two-member kingdom shares 1/divisor of each gain. */
    private static final int[] DIVISORS = {7, 5, 4};

    /** Fractions of a point owed to each player, waiting to add up. */
    private static final Map<UUID, Double> LEFTOVERS = new HashMap<>();
    /** Set while shares are being handed out, so they don't get shared in turn. Server thread only. */
    private static boolean sharing;

    private SharedXp() {
    }

    /** True while Shared XP is paying out shares. That XP was already counted for the kingdom, so it isn't again. */
    public static boolean isSharing() {
        return sharing;
    }

    /** The share of a gain that each other member gets, in a kingdom of this many members, at this perk tier. */
    public static double share(int amount, int members, int tier) {
        if (members < 2 || tier < 1) {
            return 0;
        }
        int divisor = DIVISORS[Math.min(tier, DIVISORS.length) - 1];
        return (double) amount / (members + divisor - 2);
    }

    /** Called after a player gains experience points. */
    public static void onExperienceGained(ServerPlayer player, int amount) {
        if (sharing || amount <= 0) {
            return;
        }
        Kingdom kingdom = Kingdoms.ofPlayer(player).orElse(null);
        if (kingdom == null) {
            return;
        }
        double share = share(amount, kingdom.members().size(), KingdomPerks.activeTier(kingdom, KingdomPerks.SHARED_XP));
        if (share <= 0) {
            return;
        }
        sharing = true;
        try {
            for (UUID member : kingdom.members()) {
                if (member.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer other = player.server.getPlayerList().getPlayer(member);
                if (other == null || other.isSpectator()) {
                    continue;
                }
                double owed = LEFTOVERS.getOrDefault(member, 0.0) + share;
                int whole = (int) Math.floor(owed);
                // A tiny allowance so 1/7 + ... + 1/7 (seven times) counts as a whole point despite rounding.
                if (owed - whole > 1 - 1e-9) {
                    whole++;
                }
                LEFTOVERS.put(member, Math.max(0, owed - whole));
                if (whole > 0) {
                    other.giveExperiencePoints(whole);
                }
            }
        } finally {
            sharing = false;
        }
    }
}
