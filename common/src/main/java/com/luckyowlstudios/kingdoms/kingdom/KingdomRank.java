package com.luckyowlstudios.kingdoms.kingdom;

/**
 * A player's standing in a kingdom. Officers can invite, kick and ban members; the leader can do
 * everything, including promoting, demoting, customising and changing settings.
 */
public enum KingdomRank {
    NONE,
    MEMBER,
    OFFICER,
    LEADER;

    public boolean atLeast(KingdomRank other) {
        return this.ordinal() >= other.ordinal();
    }

    public String translationKey() {
        return "kingdoms.kingdom.rank." + this.name().toLowerCase(java.util.Locale.ROOT);
    }
}
