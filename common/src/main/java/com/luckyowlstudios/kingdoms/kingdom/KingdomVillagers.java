package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Villagers moving into kingdoms. Every morning (when the overworld's day count goes up, so sleeping through the
 * night counts too) each kingdom with a Town Hall and a free home gets one new villager (and with a Tavern, one more
 * at night: see {@link KingdomTavern}). Homes are the beds in the kingdom's houses (see {@link KingdomHouses}): the
 * newcomer turns up inside a house with a free bed and makes that bed its own. The house has to be loaded (a player
 * nearby), the same way vanilla villages only grow while loaded. Server side only.
 */
public final class KingdomVillagers {

    /** Entity tag on villagers who live in a kingdom, so their deaths can be noticed cheaply. */
    public static final String RESIDENT_TAG = "kingdoms.resident";

    private static final long DAY_LENGTH = 24000L;

    private KingdomVillagers() {
    }

    /** Called every server tick by each loader. Looks once a second for a new day. */
    public static void tick(MinecraftServer server) {
        KingdomHouses.tick(server);
        KingdomTavern.tick(server);
        KingdomCelebration.tick(server);
        KingdomResearch.tick(server);
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
     * A new morning for one kingdom: if it has a Town Hall and a free home in a loaded house, a villager moves in.
     *
     * @return the villager who moved in, if one did
     */
    public static Optional<Villager> morning(MinecraftServer server, Kingdom kingdom) {
        return moveIn(server, kingdom, "kingdoms.kingdom.villager_arrived");
    }

    /**
     * If the kingdom has a Town Hall and a free home (a bed nobody has, in a valid house that's loaded), a villager
     * moves into that house and the members are told with {@code message} (given the kingdom's name, its villager count
     * and its number of homes). Homeless villagers get first pick of the free homes, and while the kingdom has more
     * villagers than homes (say a house is broken), nobody new moves in.
     *
     * @return the villager who moved in, if one did
     */
    static Optional<Villager> moveIn(MinecraftServer server, Kingdom kingdom, String message) {
        if (kingdom.townHall().isEmpty()) {
            return Optional.empty();
        }
        // Look at the loaded houses again first, so a house that's been fixed (or broken) since its last scan counts.
        Map<House, HouseScan.Result> scans = new LinkedHashMap<>();
        for (House house : KingdomHouses.of(server, kingdom)) {
            ServerLevel world = server.getLevel(house.pos().dimension());
            // Like a village, a kingdom only grows while someone is around to keep it loaded.
            if (world != null && world.isPositionEntityTicking(house.pos().pos()) && (!house.valid() || house.hasRoom())) {
                scans.put(house, KingdomHouses.rescan(world, house));
            }
        }
        KingdomHouses.settle(server, kingdom);
        int homes = KingdomHouses.homes(server, kingdom);
        if (kingdom.residents().size() >= homes) {
            return Optional.empty();
        }
        for (Map.Entry<House, HouseScan.Result> entry : scans.entrySet()) {
            House house = entry.getKey();
            ServerLevel world = server.getLevel(house.pos().dimension());
            if (world == null || !house.hasRoom()) {
                continue;
            }
            Optional<BlockPos> spot = entry.getValue().floor().stream()
                    .filter(pos -> world.noCollision(EntityType.VILLAGER.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)))
                    .findFirst();
            if (spot.isEmpty()) {
                continue;
            }
            Villager villager = EntityType.VILLAGER.create(world);
            if (villager == null) {
                return Optional.empty();
            }
            BlockPos pos = spot.get();
            villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0F, 0.0F);
            villager.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            villager.addTag(RESIDENT_TAG);
            if (!world.addFreshEntity(villager)) {
                return Optional.empty();
            }
            KingdomData.get(server).addResident(kingdom, villager.getUUID());
            KingdomHouses.moveInto(world, house, villager);
            Kingdoms.broadcast(server, kingdom, Component.translatable(message,
                    kingdom.displayName(), kingdom.residents().size(), homes));
            return Optional.of(villager);
        }
        return Optional.empty();
    }

    /** A villager joined from outside (a worker taking a job): it gets a free home if there is one. */
    private static void joined(ServerLevel world, Kingdom kingdom, Villager villager) {
        KingdomData data = KingdomData.get(world.getServer());
        data.removeResident(villager.getUUID());
        villager.addTag(RESIDENT_TAG);
        data.addResident(kingdom, villager.getUUID());
        KingdomHouses.settle(world.getServer(), kingdom);
    }

    /**
     * The villager who works at a Town Hall (the Advisor) belongs to that hall's kingdom. Called now and then for
     * every Advisor, so it also catches ones who took the job before the kingdom was founded. The Advisor joins even
     * if the kingdom has no free home (it's homeless until one turns up), and takes up a home from then on. A villager from another kingdom
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
        joined(world, kingdom, villager);
        Kingdoms.broadcast(world.getServer(), kingdom, Component.translatable("kingdoms.kingdom.advisor_joined",
                kingdom.displayName(), kingdom.residents().size(), KingdomHouses.homes(world.getServer(), kingdom)));
        return true;
    }

    /**
     * A villager who works at one of the kingdom's work blocks (a Scholar at a Scholar's Desk, an Innkeeper at a Tavern,
     * a Lookout at a Watchtower) on a kingdom's land belongs to that kingdom, the same way the Advisor does. Without
     * this, only villagers who moved in through the Town Hall count, so a villager from a nearby village (or a spawn
     * egg) that takes the job would never do any work. Like the Advisor, it joins even without a free home. Called
     * now and then for every villager.
     *
     * @return true if the villager just joined
     */
    public static boolean checkWorker(Villager villager) {
        if (!(villager.level() instanceof ServerLevel world)) {
            return false;
        }
        var profession = villager.getVillagerData().getProfession();
        net.minecraft.world.level.block.Block workBlock;
        if (profession == ModVillagers.SCHOLAR_PROFESSION.get()) {
            workBlock = com.luckyowlstudios.kingdoms.registry.ModBlocks.SCHOLARS_DESK.get();
        } else if (profession == ModVillagers.INNKEEPER_PROFESSION.get()) {
            workBlock = com.luckyowlstudios.kingdoms.registry.ModBlocks.TAVERN.get();
        } else if (profession == ModVillagers.LOOKOUT_PROFESSION.get()) {
            workBlock = com.luckyowlstudios.kingdoms.registry.ModBlocks.WATCHTOWER.get();
        } else {
            return false;
        }
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(world.dimension()) || !world.isLoaded(site.get().pos())
                || !world.getBlockState(site.get().pos()).is(workBlock)) {
            return false;
        }
        KingdomData data = KingdomData.get(world.getServer());
        Optional<Kingdom> found = data.claimedBy(ChunkClaim.of(site.get()));
        if (found.isEmpty() || found.get().residents().contains(villager.getUUID())) {
            return false;
        }
        Kingdom kingdom = found.get();
        joined(world, kingdom, villager);
        Kingdoms.broadcast(world.getServer(), kingdom, Component.translatable("kingdoms.kingdom.worker_joined",
                // The job's name, e.g. "Scholar" (Villager#getTypeName does the same, but isn't public).
                Component.translatable("entity.minecraft.villager."
                        + net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath()),
                kingdom.displayName(),
                kingdom.residents().size(), KingdomHouses.homes(world.getServer(), kingdom)));
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
