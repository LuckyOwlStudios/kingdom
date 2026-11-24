package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.network.PerkUnlockedPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * The kingdom perk tree, and unlocking and upgrading perks.
 * <p>
 * A kingdom earns {@link #POINTS_PER_LEVEL} perk points for each level it has, and spends them on perk tiers. A tier
 * needs the kingdom to be at that tier's level; the first tier also needs the perk's prerequisites. Only players of
 * {@link #UNLOCK_RANK} spend points. If an operator lowers a kingdom's level, tiers above the new level stop working
 * until it climbs back; nothing is taken away.
 * <p>
 * To add a perk: add a constant below with {@link #register}, add its text to the language file (see
 * {@link KingdomPerk}), and make the code it affects check {@link #activeTier}.
 */
public final class KingdomPerks {

    public static final int POINTS_PER_LEVEL = 3;
    /** Who can spend the kingdom's perk points. */
    public static final KingdomRank UNLOCK_RANK = KingdomRank.LEADER;

    private static final Map<ResourceLocation, KingdomPerk> PERKS = new LinkedHashMap<>();

    /** The tree's branches, one per row, top to bottom. Each is named by {@code kingdoms.perk.branch.<name>}. */
    public static final List<String> BRANCHES = List.of("prosperity", "defense", "community", "growth");

    // ---- Prosperity ----

    /** Crops on the kingdom's land grow faster. See {@link KingdomPerkEffects#bountifulHarvestChance}. */
    public static final KingdomPerk BOUNTIFUL_HARVEST = register("bountiful_harvest", Items.WHEAT, 0, 0,
            List.of(tier(1, 1), tier(2, 1), tier(4, 2)));

    /** Villagers on the kingdom's land give its members better prices. See {@link KingdomPerkEffects#tradeDiscount}. */
    public static final KingdomPerk TRADE_DEALS = register("trade_deals", Items.BELL, 1, 0,
            List.of(tier(2, 1), tier(3, 1), tier(5, 2)), BOUNTIFUL_HARVEST);

    /** Villagers living in the kingdom pay emeralds into the Town Hall's treasury each morning. See {@link TaxCollector}. */
    public static final KingdomPerk TAX_COLLECTOR = register("tax_collector", Items.EMERALD, 2, 0,
            List.of(tier(2, 1), tier(3, 2), tier(4, 2)), TRADE_DEALS);

    // ---- Defense ----

    /** Fewer hostile mobs spawn on the kingdom's land. See {@link KingdomPerkEffects#watchtowerBlocksSpawn}. */
    public static final KingdomPerk WATCHTOWERS = register("watchtowers", Items.SPYGLASS, 0, 1,
            List.of(tier(1, 1), tier(3, 1), tier(4, 2)));

    /** Outsiders can't break, blow up or use blocks on the kingdom's land. See {@link KingdomPerkEffects}. */
    public static final KingdomPerk FORTIFIED_WALLS = register("fortified_walls", Items.STONE_BRICK_WALL, 1, 1,
            List.of(tier(2, 1), tier(3, 2), tier(4, 2)), WATCHTOWERS);

    /** Members near the Town Hall get buffs while a raid is on. See {@link KingdomPerkEffects#playerTick}. */
    public static final KingdomPerk RALLY = register("rally", Items.GOAT_HORN, 2, 1,
            List.of(tier(3, 1), tier(4, 1), tier(5, 2)), FORTIFIED_WALLS);

    // ---- Community ----

    /** When a member gains XP, the other members online get a share of it; more with each tier. See {@link SharedXp}. */
    public static final KingdomPerk SHARED_XP = register("shared_xp", Items.EXPERIENCE_BOTTLE, 0, 2,
            List.of(tier(2, 1), tier(3, 1), tier(5, 2)));

    /** Members on the kingdom's land slowly get fed and heal. See {@link KingdomPerkEffects#playerTick}. */
    public static final KingdomPerk WELL_FED = register("well_fed", Items.BREAD, 1, 2,
            List.of(tier(2, 1), tier(3, 1), tier(4, 2)), SHARED_XP);

    /** Members can teleport to the Town Hall, with a cooldown. See {@link Homecoming}. */
    public static final KingdomPerk HOMECOMING = register("homecoming", Items.ENDER_PEARL, 2, 2,
            List.of(tier(3, 1), tier(4, 1), tier(5, 1)), WELL_FED);

    /** A storage chest the whole kingdom shares, opened at the Town Hall. See {@link SharedVault}. */
    public static final KingdomPerk SHARED_VAULT = register("shared_vault", Items.CHEST, 3, 2,
            List.of(tier(3, 1), tier(4, 1), tier(5, 1)), HOMECOMING);

    // ---- Growth ----

    /** More villagers move in each morning. See {@link KingdomVillagers#tick}. */
    public static final KingdomPerk WELCOMING = register("welcoming", Items.VILLAGER_SPAWN_EGG, 0, 3,
            List.of(tier(1, 1), tier(3, 1), tier(5, 2)));

    /** The kingdom's land reaches one more chunk out per tier. See {@link Kingdom#claimRadius}. */
    public static final KingdomPerk EXPANSION = register("expansion", Items.MAP, 1, 3,
            List.of(tier(2, 1), tier(3, 2), tier(4, 2)), WELCOMING);

    /** Members are faster on paths in the kingdom's land. See {@link KingdomPerkEffects#playerTick}. */
    public static final KingdomPerk SWIFT_ROADS = register("swift_roads", Items.DIRT_PATH, 2, 3,
            List.of(tier(2, 1), tier(3, 1), tier(4, 2)), EXPANSION);

    private KingdomPerks() {
    }

    private static KingdomPerk.Tier tier(int requiredLevel, int cost) {
        return new KingdomPerk.Tier(requiredLevel, cost);
    }

    /**
     * Adds a perk to the tree.
     *
     * @param prerequisites the perks that must be unlocked first
     */
    private static KingdomPerk register(String name, Item icon, int column, int row, List<KingdomPerk.Tier> tiers,
                                        KingdomPerk... prerequisites) {
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("Perk " + name + " has no tiers");
        }
        KingdomPerk perk = new KingdomPerk(Constants.id(name), icon, List.copyOf(tiers),
                Arrays.stream(prerequisites).map(KingdomPerk::id).toList(), column, row);
        if (PERKS.putIfAbsent(perk.id(), perk) != null) {
            throw new IllegalStateException("Two perks with the id " + perk.id());
        }
        return perk;
    }

    /** The tier of a perk that is working for whichever kingdom has claimed this block, or 0. */
    public static int landTier(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, KingdomPerk perk) {
        return KingdomData.get(level.getServer()).claimedBy(ChunkClaim.of(net.minecraft.core.GlobalPos.of(level.dimension(), pos)))
                .map(kingdom -> activeTier(kingdom, perk)).orElse(0);
    }

    /** Every perk, in the order they were added. */
    public static Collection<KingdomPerk> all() {
        return Collections.unmodifiableCollection(PERKS.values());
    }

    public static Optional<KingdomPerk> byId(ResourceLocation id) {
        return Optional.ofNullable(PERKS.get(id));
    }

    public static int earnedPoints(int level) {
        return level * POINTS_PER_LEVEL;
    }

    /** Perk points spent on these tiers (perk id to tier), ignoring perks that no longer exist. */
    public static int spentPoints(Map<ResourceLocation, Integer> tiers) {
        int spent = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : tiers.entrySet()) {
            KingdomPerk perk = PERKS.get(entry.getKey());
            if (perk != null) {
                for (int n = 1; n <= Math.min(entry.getValue(), perk.maxTier()); n++) {
                    spent += perk.tier(n).cost();
                }
            }
        }
        return spent;
    }

    public static int availablePoints(Kingdom kingdom) {
        return Math.max(0, earnedPoints(kingdom.level()) - spentPoints(kingdom.perks()));
    }

    /** The highest tier of the perk that is working for the kingdom: unlocked, and the kingdom is at its level. 0 if none. */
    public static int activeTier(Kingdom kingdom, KingdomPerk perk) {
        return activeTier(perk, kingdom.level(), kingdom.perkTier(perk.id()));
    }

    /** The highest tier up to {@code unlocked} that works at this kingdom level. Shared with the screen. */
    public static int activeTier(KingdomPerk perk, int level, int unlocked) {
        int tier = Math.min(unlocked, perk.maxTier());
        while (tier > 0 && level < perk.tier(tier).requiredLevel()) {
            tier--;
        }
        return tier;
    }

    public static boolean isActive(Kingdom kingdom, KingdomPerk perk) {
        return activeTier(kingdom, perk) > 0;
    }

    /** Why the next tier of a perk can't be bought right now, if it can't. The screen shows the same reason. */
    public enum Blocker {
        NONE, MAXED, LEVEL_TOO_LOW, MISSING_PREREQUISITE, NOT_ENOUGH_POINTS;

        public String translationKey() {
            return "kingdoms.perk.blocker." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Checks whether the next tier of a perk could be bought.
     *
     * @param tiers the tier the kingdom has of each perk id (0 if none)
     */
    public static Blocker blocker(KingdomPerk perk, int level, int availablePoints, ToIntFunction<ResourceLocation> tiers) {
        int next = tiers.applyAsInt(perk.id()) + 1;
        if (next > perk.maxTier()) {
            return Blocker.MAXED;
        }
        if (level < perk.tier(next).requiredLevel()) {
            return Blocker.LEVEL_TOO_LOW;
        }
        if (next == 1 && !perk.prerequisites().stream().allMatch(id -> tiers.applyAsInt(id) > 0)) {
            return Blocker.MISSING_PREREQUISITE;
        }
        if (availablePoints < perk.tier(next).cost()) {
            return Blocker.NOT_ENOUGH_POINTS;
        }
        return Blocker.NONE;
    }

    public static Blocker blocker(Kingdom kingdom, KingdomPerk perk) {
        return blocker(perk, kingdom.level(), availablePoints(kingdom), kingdom::perkTier);
    }

    /** The player asked to unlock or upgrade a perk for their kingdom, from the Perks tab. */
    public static boolean upgrade(ServerPlayer player, String perkId) {
        ResourceLocation id = ResourceLocation.tryParse(perkId);
        Optional<KingdomPerk> perk = id == null ? Optional.empty() : byId(id);
        return perk.isPresent() && upgrade(player, perk.get());
    }

    /** Buys the next tier of a perk (tier 1 unlocks it). */
    public static boolean upgrade(ServerPlayer player, KingdomPerk perk) {
        Optional<Kingdom> found = Kingdoms.ofPlayer(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_in_kingdom"));
            return false;
        }
        Kingdom kingdom = found.get();
        if (!kingdom.rank(player.getUUID()).atLeast(UNLOCK_RANK)) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_leader", kingdom.displayName()));
            return false;
        }
        Blocker blocker = blocker(kingdom, perk);
        if (blocker != Blocker.NONE) {
            int next = Math.min(kingdom.perkTier(perk.id()) + 1, perk.maxTier());
            player.sendSystemMessage(Component.translatable(blocker.translationKey(), perk.displayName(),
                    perk.tier(next).requiredLevel(), perk.tier(next).cost()));
            return false;
        }
        int tier = kingdom.perkTier(perk.id()) + 1;
        KingdomData.get(player.server).setPerkTier(kingdom, perk, tier);
        // A toast for everyone in the kingdom who's online, as well as the chat message.
        PerkUnlockedPayload toast = new PerkUnlockedPayload(perk.id(), tier);
        for (java.util.UUID member : kingdom.members()) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(toast));
            }
        }
        Kingdoms.broadcast(player.server, kingdom, tier == 1
                ? Component.translatable("kingdoms.perk.unlocked", kingdom.displayName(), perk.displayName())
                : Component.translatable("kingdoms.perk.upgraded", kingdom.displayName(), perk.displayName(), tier));
        return true;
    }
}
