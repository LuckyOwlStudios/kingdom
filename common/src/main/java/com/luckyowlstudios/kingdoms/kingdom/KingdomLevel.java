package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * One step of a kingdom's growth. Every level allows more villagers to move in and more land around the Town Hall.
 * The whole table is {@link #LEVELS}; change it there to rebalance.
 *
 * @param level       the level number, from 1
 * @param villagerCap how many villagers can live in the kingdom at this level
 * @param claimRadius how many chunks out from the Town Hall's chunk the kingdom's land reaches (0 is the hall's chunk alone)
 * @param cost        how many {@link #CURRENCY} it costs to reach this level from the one before
 */
public record KingdomLevel(int level, int villagerCap, int claimRadius, int cost) {

    /** What levelling up is paid in, for now. */
    public static final Item CURRENCY = Items.EMERALD;

    public static final List<KingdomLevel> LEVELS = List.of(
            new KingdomLevel(1, 2, 0, 0),
            new KingdomLevel(2, 4, 1, 16),
            new KingdomLevel(3, 7, 2, 32),
            new KingdomLevel(4, 10, 3, 48),
            new KingdomLevel(5, 15, 4, 64));

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

    public boolean isMax() {
        return this.level >= MAX_LEVEL;
    }
}
