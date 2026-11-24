package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * What the perks that change the world do. The mixins and the player tick call in here; each method checks the perk.
 * Server side only.
 */
public final class KingdomPerkEffects {

    private KingdomPerkEffects() {
    }

    private static Optional<Kingdom> landOwner(ServerLevel level, BlockPos pos) {
        return KingdomData.get(level.getServer()).claimedBy(ChunkClaim.of(GlobalPos.of(level.dimension(), pos)));
    }

    // ---- Bountiful Harvest ----

    /** Extra chance per tier that a crop on the land grows another stage when it gets a random tick. */
    private static final float[] HARVEST_CHANCE = {0.25F, 0.5F, 1.0F};

    public static float bountifulHarvestChance(ServerLevel level, BlockPos pos) {
        int tier = KingdomPerks.landTier(level, pos, KingdomPerks.BOUNTIFUL_HARVEST);
        return tier == 0 ? 0 : HARVEST_CHANCE[tier - 1];
    }

    // ---- Trade Deals ----

    /** Share of the price knocked off per tier. */
    private static final float[] TRADE_DISCOUNT = {0.1F, 0.2F, 0.3F};

    /**
     * How many fewer items a member pays for a trade with a villager on their own kingdom's land, or 0. Always at
     * least one off once the perk is unlocked.
     */
    public static int tradeDiscount(ServerLevel level, BlockPos villager, Player player, int price) {
        Optional<Kingdom> owner = landOwner(level, villager);
        if (owner.isEmpty() || !owner.get().isMember(player.getUUID()) || price <= 1) {
            return 0;
        }
        int tier = KingdomPerks.activeTier(owner.get(), KingdomPerks.TRADE_DEALS);
        return tier == 0 ? 0 : Math.max(1, Mth.floor(price * TRADE_DISCOUNT[tier - 1]));
    }

    // ---- Watchtowers ----

    /** Share of natural hostile spawns stopped on the land, per tier. */
    private static final float[] WATCHTOWER_CHANCE = {1 / 3F, 2 / 3F, 1.0F};

    /** True if a hostile mob that was about to spawn naturally on a kingdom's land shouldn't. */
    public static boolean watchtowerBlocksSpawn(ServerLevel level, Mob mob) {
        if (!(mob instanceof Enemy)) {
            return false;
        }
        int tier = KingdomPerks.landTier(level, mob.blockPosition(), KingdomPerks.WATCHTOWERS);
        return tier > 0 && level.getRandom().nextFloat() < WATCHTOWER_CHANCE[tier - 1];
    }

    // ---- Fortified Walls ----

    /** Tier 1: outsiders can't break blocks on the land. */
    public static final int WALLS_BREAKING = 1;
    /** Tier 2: explosions don't break blocks on the land. */
    public static final int WALLS_EXPLOSIONS = 2;
    /** Tier 3: outsiders can't place or use blocks on the land. */
    public static final int WALLS_USING = 3;

    /**
     * True if Fortified Walls, at {@code neededTier} or higher, keeps this player from touching the block. Members,
     * allies, and operators in creative mode are never stopped.
     */
    public static boolean wallsStop(ServerPlayer player, BlockPos pos, int neededTier) {
        ServerLevel level = player.serverLevel();
        Optional<Kingdom> owner = landOwner(level, pos);
        if (owner.isEmpty() || KingdomPerks.activeTier(owner.get(), KingdomPerks.FORTIFIED_WALLS) < neededTier) {
            return false;
        }
        Kingdom kingdom = owner.get();
        if (kingdom.isMember(player.getUUID()) || (player.isCreative() && player.hasPermissions(2))) {
            return false;
        }
        Optional<Kingdom> own = Kingdoms.ofPlayer(player);
        return own.isEmpty() || !KingdomDiplomacy.get(player.server).areAllied(own.get().id(), kingdom.id());
    }

    /** True if an explosion shouldn't break this block. */
    public static boolean wallsStopExplosion(ServerLevel level, BlockPos pos) {
        return KingdomPerks.landTier(level, pos, KingdomPerks.FORTIFIED_WALLS) >= WALLS_EXPLOSIONS;
    }

    // ---- Once a second for each player: Rally, Well Fed, Swift Roads ----

    /** How close to the Town Hall members have to be for Rally. */
    private static final double RALLY_RANGE_SQR = 64.0 * 64.0;

    public static void playerTick(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        Optional<Kingdom> found = Kingdoms.ofPlayer(player);
        if (found.isEmpty()) {
            return;
        }
        Kingdom kingdom = found.get();
        ServerLevel level = player.serverLevel();
        int second = player.tickCount / 20;

        int rally = KingdomPerks.activeTier(kingdom, KingdomPerks.RALLY);
        if (rally > 0) {
            kingdom.townHall().filter(hall -> hall.dimension().equals(level.dimension())
                            && player.distanceToSqr(hall.pos().getCenter()) <= RALLY_RANGE_SQR
                            && level.getRaidAt(hall.pos()) != null)
                    .ifPresent(hall -> {
                        buff(player, MobEffects.DAMAGE_RESISTANCE, rally >= 3 ? 1 : 0);
                        if (rally >= 2) {
                            buff(player, MobEffects.DAMAGE_BOOST, 0);
                        }
                        if (rally >= 3) {
                            buff(player, MobEffects.REGENERATION, 0);
                        }
                    });
        }

        boolean onOwnLand = landOwner(level, player.blockPosition()).filter(owner -> owner == kingdom).isPresent();
        if (!onOwnLand) {
            return;
        }

        int wellFed = KingdomPerks.activeTier(kingdom, KingdomPerks.WELL_FED);
        if (wellFed > 0) {
            int feedEvery = wellFed >= 3 ? 10 : 20;
            if (second % feedEvery == 0 && player.getFoodData().needsFood()) {
                player.getFoodData().eat(1, 0.5F);
            }
            int healEvery = wellFed >= 3 ? 5 : 10;
            if (wellFed >= 2 && second % healEvery == 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0F);
            }
        }

        int roads = KingdomPerks.activeTier(kingdom, KingdomPerks.SWIFT_ROADS);
        if (roads > 0) {
            boolean onPath = level.getBlockState(player.getOnPos()).is(Blocks.DIRT_PATH)
                    || level.getBlockState(player.blockPosition()).is(Blocks.DIRT_PATH);
            if (onPath) {
                buff(player, MobEffects.MOVEMENT_SPEED, roads >= 2 ? 1 : 0);
            } else if (roads >= 3) {
                buff(player, MobEffects.MOVEMENT_SPEED, 0);
            }
        }
    }

    /** A short, quiet effect, topped up every second while it applies. */
    private static void buff(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, 50, amplifier, true, false, true));
    }
}
