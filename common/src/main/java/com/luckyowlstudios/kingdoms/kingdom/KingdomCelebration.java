package com.luckyowlstudios.kingdoms.kingdom;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A kingdom levelled up: for a few seconds fireworks in its colour go up from the Town Hall, and its villagers
 * celebrate like after a won raid, jumping, cheering and setting off fireworks of their own. Nothing is saved;
 * a celebration cut short by a restart just ends. Server side only.
 */
public final class KingdomCelebration {

    /** How long a celebration lasts, in ticks. */
    public static final int DURATION = 200;
    private static final FireworkExplosion.Shape[] SHAPES = {
            FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.LARGE_BALL,
            FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};

    /** Ticks left for each celebrating kingdom, by kingdom id. Server thread only. */
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    private KingdomCelebration() {
    }

    public static void start(Kingdom kingdom) {
        if (kingdom.townHall().isPresent()) {
            ACTIVE.put(kingdom.id(), DURATION);
        }
    }

    public static boolean isCelebrating(Kingdom kingdom) {
        return ACTIVE.containsKey(kingdom.id());
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        KingdomData data = KingdomData.get(server);
        for (Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            Kingdom kingdom = data.get(entry.getKey()).orElse(null);
            int left = entry.getValue() - 1;
            if (kingdom == null || kingdom.townHall().isEmpty() || left <= 0) {
                it.remove();
                continue;
            }
            entry.setValue(left);
            celebrate(server, kingdom, kingdom.townHall().get(), left);
        }
    }

    private static void celebrate(MinecraftServer server, Kingdom kingdom, GlobalPos hall, int ticksLeft) {
        ServerLevel world = server.getLevel(hall.dimension());
        if (world == null || !world.isPositionEntityTicking(hall.pos())) {
            return; // nobody is around to see it
        }
        RandomSource random = world.getRandom();
        if (ticksLeft % 15 == 0) {
            BlockPos pos = hall.pos();
            launch(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, kingdom.color(), random);
        }
        for (UUID id : List.copyOf(kingdom.residents())) {
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Villager villager) || villager.isSleeping()) {
                continue;
            }
            // The same moves as vanilla's celebration after a raid.
            if (villager.onGround() && random.nextInt(12) == 0) {
                villager.getJumpControl().jump();
            }
            if (random.nextInt(50) == 0) {
                world.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            }
            if (random.nextInt(90) == 0) {
                launch(world, villager.getX(), villager.getEyeY(), villager.getZ(), kingdom.color(), random);
            }
        }
    }

    /** A firework rocket in the kingdom's colour, fading to white or gold. */
    private static void launch(ServerLevel world, double x, double y, double z, DyeColor color, RandomSource random) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        DyeColor fade = random.nextBoolean() ? DyeColor.WHITE : DyeColor.YELLOW;
        FireworkExplosion explosion = new FireworkExplosion(SHAPES[random.nextInt(SHAPES.length)],
                IntList.of(color.getFireworkColor()), IntList.of(fade.getFireworkColor()), true, random.nextBoolean());
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(explosion)));
        world.addFreshEntity(new FireworkRocketEntity(world, x, y, z, rocket));
    }
}
