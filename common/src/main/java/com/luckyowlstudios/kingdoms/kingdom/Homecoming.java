package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Homecoming perk: members teleport to their Town Hall with {@code /kingdom home} or the button in the Perks tab.
 * The cooldown gets shorter with each tier (see {@link #COOLDOWN_SECONDS}). Cooldowns are kept until the server stops.
 */
public final class Homecoming {

    /** Seconds between teleports, for each tier. */
    private static final int[] COOLDOWN_SECONDS = {20 * 60, 10 * 60, 3 * 60};

    /** When each player last went home, in server ticks. */
    private static final Map<UUID, Integer> LAST_USED = new HashMap<>();

    private Homecoming() {
    }

    public static int cooldownSeconds(int tier) {
        return COOLDOWN_SECONDS[Math.clamp(tier, 1, COOLDOWN_SECONDS.length) - 1];
    }

    /** Seconds before the player can go home again, or 0. */
    public static int secondsLeft(ServerPlayer player, int tier) {
        Integer last = LAST_USED.get(player.getUUID());
        if (last == null || last > player.server.getTickCount()) {
            return 0;
        }
        int ticksLeft = cooldownSeconds(tier) * 20 - (player.server.getTickCount() - last);
        return Math.max(0, (ticksLeft + 19) / 20);
    }

    public static boolean goHome(ServerPlayer player) {
        Optional<Kingdom> found = Kingdoms.ofPlayer(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_in_kingdom"));
            return false;
        }
        Kingdom kingdom = found.get();
        int tier = KingdomPerks.activeTier(kingdom, KingdomPerks.HOMECOMING);
        if (tier == 0) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.not_unlocked", KingdomPerks.HOMECOMING.displayName()));
            return false;
        }
        if (kingdom.townHall().isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.homecoming.no_town_hall"));
            return false;
        }
        int left = secondsLeft(player, tier);
        if (left > 0) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.homecoming.cooldown", left / 60, String.format("%02d", left % 60)));
            return false;
        }
        GlobalPos hall = kingdom.townHall().get();
        ServerLevel level = player.server.getLevel(hall.dimension());
        if (level == null) {
            return false;
        }
        BlockPos spot = landingSpot(level, hall.pos());
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.playSound(null, spot, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        LAST_USED.put(player.getUUID(), player.server.getTickCount());
        return true;
    }

    /** Next to the Town Hall where a player fits with ground underneath, or on top of it if there's no such spot. */
    private static BlockPos landingSpot(ServerLevel level, BlockPos hall) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos pos = hall.relative(direction).above(dy);
                if (level.noCollision(EntityType.PLAYER.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    return pos;
                }
            }
        }
        return hall.above();
    }
}
