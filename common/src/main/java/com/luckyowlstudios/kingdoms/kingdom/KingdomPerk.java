package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * One perk in the kingdom perk tree. Perks are listed in {@link KingdomPerks}; what each one does lives with the
 * code it changes (Shared XP in {@link SharedXp}, for example), which asks {@link KingdomPerks#activeTier} first.
 * <p>
 * A perk has one or more tiers. Unlocking it gives tier 1; each upgrade after that is the next tier, with its own
 * level requirement, cost and research time. Tiers are researched at a Scholar's Desk (see {@link KingdomResearch}).
 * <p>
 * Text comes from the language file: {@code kingdoms.perk.<path>} (name), {@code kingdoms.perk.<path>.description}
 * (what the perk is about) and {@code kingdoms.perk.<path>.tier.<n>} (what tier {@code n} gives).
 *
 * @param id            unique id, saved with each kingdom that unlocks it
 * @param icon          the item drawn for it in the tree
 * @param tiers         its tiers, first to last
 * @param prerequisites perks that need at least tier 1 before this one can be unlocked; drawn as lines in the tree
 * @param column        where it sits in the tree, left to right
 * @param row           where it sits in the tree, top to bottom
 */
public record KingdomPerk(ResourceLocation id, Item icon, List<Tier> tiers, List<ResourceLocation> prerequisites,
                          int column, int row) {

    /**
     * @param requiredLevel   the kingdom level needed to research this tier (and to keep it working)
     * @param cost            perk points it takes
     * @param researchSeconds how long one Scholar takes to research it, in seconds; more Scholars share the work
     */
    public record Tier(int requiredLevel, int cost, int researchSeconds) {
    }

    public int maxTier() {
        return this.tiers.size();
    }

    /** Tier {@code n}, counting from 1. */
    public Tier tier(int n) {
        return this.tiers.get(n - 1);
    }

    public String translationKey() {
        return "kingdoms.perk." + this.id.getPath();
    }

    public Component displayName() {
        return Component.translatable(this.translationKey());
    }

    public Component description() {
        return Component.translatable(this.translationKey() + ".description");
    }

    public Component tierDescription(int n) {
        return Component.translatable(this.translationKey() + ".tier." + n);
    }
}
