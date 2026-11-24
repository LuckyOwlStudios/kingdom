package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;

/**
 * Villagers moving into kingdoms. Every morning (when the overworld's day count goes up, so sleeping through the
 * night counts too) each kingdom with a Town Hall and room under its level's villager cap gets one new villager,
 * who appears near the hall. The hall has to be loaded (a player nearby), the same way vanilla villages only grow
 * while loaded. Server side only.
 */
public final class KingdomVillagers {

    /** Entity tag on villagers who live in a kingdom, so their deaths can be noticed cheaply. */
    public static final String RESIDENT_TAG = "kingdoms.resident";

    private static final long DAY_LENGTH = 24000L;
    /** How far from the Town Hall, in blocks, a new villager can turn up. */
    private static final int ARRIVAL_RANGE = 6;

    private KingdomVillagers() {
    }

    /** Called every server tick by each loader. Looks once a second for a new day. */
    public static void tick(MinecraftServer server) {
        KingdomCelebration.tick(server);
        Watchtowers.tick(server);
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long day = server.overworld().getDayTime() / DAY_LENGTH;
        KingdomData data = KingdomData.get(server);
        long last = data.lastDay();
        if (last == day) {
            return;
        }
        data.setLastDay(day);
        // The first check in a world, or time set backwards with /time: no arrivals, just note the day.
        if (last < 0 || day < last) {
            return;
        }
        for (Kingdom kingdom : List.copyOf(data.all())) {
            TaxCollector.morning(server, kingdom);
            // One villager a morning, and one more for each tier of Welcoming.
            int arrivals = 1 + KingdomPerks.activeTier(kingdom, KingdomPerks.WELCOMING);
            for (int i = 0; i < arrivals && morning(server, kingdom).isPresent(); i++) {
            }
        }
    }

    /**
     * A new morning for one kingdom: if it has a Town Hall and room for another villager, one moves in.
     *
     * @return the villager who moved in, if one did
     */
    public static Optional<Villager> morning(MinecraftServer server, Kingdom kingdom) {
        KingdomLevel level = kingdom.levelInfo();
        if (kingdom.residents().size() >= level.villagerCap() || kingdom.townHall().isEmpty()) {
            return Optional.empty();
        }
        GlobalPos hall = kingdom.townHall().get();
        ServerLevel world = server.getLevel(hall.dimension());
        // Like a village, a kingdom only grows while someone is around to keep it loaded.
        if (world == null || !world.isPositionEntityTicking(hall.pos())) {
            return Optional.empty();
        }
        Villager villager = EntityType.VILLAGER.create(world);
        if (villager == null) {
            return Optional.empty();
        }
        BlockPos pos = arrivalSpot(world, hall.pos(), world.getRandom());
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0F, 0.0F);
        villager.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        villager.addTag(RESIDENT_TAG);
        if (!world.addFreshEntity(villager)) {
            return Optional.empty();
        }
        KingdomData.get(server).addResident(kingdom, villager.getUUID());
        Kingdoms.broadcast(server, kingdom, Component.translatable("kingdoms.kingdom.villager_arrived",
                kingdom.displayName(), kingdom.residents().size(), level.villagerCap()));
        return Optional.of(villager);
    }

    /** A spot on the ground near the Town Hall with room for a villager, or just above the hall if none turns up. */
    private static BlockPos arrivalSpot(ServerLevel world, BlockPos hall, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = hall.getX() + random.nextIntBetweenInclusive(-ARRIVAL_RANGE, ARRIVAL_RANGE);
            int z = hall.getZ() + random.nextIntBetweenInclusive(-ARRIVAL_RANGE, ARRIVAL_RANGE);
            if (Math.abs(x - hall.getX()) < 2 && Math.abs(z - hall.getZ()) < 2) {
                continue; // not on top of the hall
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (world.isPositionEntityTicking(pos) && Math.abs(y - hall.getY()) <= 8 && world.getFluidState(pos.below()).isEmpty()
                    && world.noCollision(EntityType.VILLAGER.getSpawnAABB(x + 0.5, y, z + 0.5))) {
                return pos;
            }
        }
        return hall.above();
    }

    /**
     * The villager who works at a Town Hall (the Advisor) belongs to that hall's kingdom. Called now and then for
     * every Advisor, so it also catches ones who took the job before the kingdom was founded. The Advisor joins even
     * if the kingdom is at its villager cap, and counts toward it from then on. A villager from another kingdom
     * moves over.
     *
     * @return true if the villager just joined
     */
    public static boolean checkAdvisor(Villager villager) {
        if (!(villager.level() instanceof ServerLevel world)
                || villager.getVillagerData().getProfession() != ModVillagers.ADVISOR_PROFESSION.get()) {
            return false;
        }
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty()) {
            return false;
        }
        KingdomData data = KingdomData.get(world.getServer());
        Optional<Kingdom> found = data.atTownHall(site.get());
        if (found.isEmpty() || found.get().residents().contains(villager.getUUID())) {
            return false;
        }
        Kingdom kingdom = found.get();
        data.removeResident(villager.getUUID());
        villager.addTag(RESIDENT_TAG);
        data.addResident(kingdom, villager.getUUID());
        Kingdoms.broadcast(world.getServer(), kingdom, Component.translatable("kingdoms.kingdom.advisor_joined",
                kingdom.displayName(), kingdom.residents().size(), kingdom.levelInfo().villagerCap()));
        return true;
    }

    /** An entity is being removed for good. If it was one of a kingdom's villagers, it no longer counts. */
    public static void onRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!reason.shouldDestroy() || !(entity instanceof Villager) || !entity.getTags().contains(RESIDENT_TAG)
                || !(entity.level() instanceof ServerLevel world)) {
            return;
        }
        KingdomData.get(world.getServer()).removeResident(entity.getUUID());
    }
}
