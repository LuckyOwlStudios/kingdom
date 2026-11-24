package com.luckyowlstudios.kingdoms.kingdom;

import java.util.List;

/**
 * One step of a kingdom's growth. Every level allows more land around the Town Hall. (How many villagers can live in
 * the kingdom is up to its houses: see {@link KingdomHouses}.)
 * The whole table is {@link #LEVELS}; change it there to rebalance.
 *
 * @param level       the level number, from 1
 * @param claimRadius how many chunks out from the Town Hall's chunk the kingdom's land reaches (0 is the hall's chunk alone)
 * @param xp          how much kingdom XP it takes to reach this level from the one before. Members earn kingdom XP
 *                    whenever they gain experience (see {@link KingdomXp})
 */
public record KingdomLevel(int level, int claimRadius, int xp) {

    public static final List<KingdomLevel> LEVELS = List.of(
            new KingdomLevel(1, 0, 0),
            new KingdomLevel(2, 1, 100),
            new KingdomLevel(3, 2, 250),
            new KingdomLevel(4, 3, 500),
            new KingdomLevel(5, 4, 1000));

    public static final int MAX_LEVEL = LEVELS.size();

    /** The entry for a level, clamped to the table. */
    public static KingdomLevel of(int level) {
        return LEVELS.get(Math.clamp(level, 1, MAX_LEVEL) - 1);
    }

    /** The most chunks a kingdom can hold at this level: a square around the Town Hall's chunk. */
    public int landCap() {
        int side = 2 * this.claimRadius + 1;
        return side * side;
    }

    /** XP needed to go from this level to the next, or 0 at the top. */
    public int xpToNext() {
        return this.isMax() ? 0 : of(this.level + 1).xp();
    }

    public boolean isMax() {
        return this.level >= MAX_LEVEL;
    }
}
