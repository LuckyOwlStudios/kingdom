package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Tavern and its Innkeeper. Villagers normally move into a kingdom only in the morning (see
 * {@link KingdomVillagers}). A kingdom with a working Innkeeper also gets one traveller moving in each night, when
 * night falls (or when the night is slept through), still only into a free home in a loaded house (see
 * {@link KingdomHouses}).
 * <p>
 * An Innkeeper counts when it lives in the kingdom (a resident), is loaded, and its job site is a Tavern on the
 * kingdom's land. Unlike a Scholar it counts while asleep: the tavern stays open at night. Server side only.
 */
public final class KingdomTavern {

    private static final long DAY_LENGTH = 24000L;
    /** The day time at which night falls, as far as the Tavern is concerned (when monsters start to spawn). */
    public static final long NIGHT_START = 13000L;

    private KingdomTavern() {
    }

    /** Which night it is: goes up by one each time the overworld passes {@link #NIGHT_START}. */
    public static long nightOf(long dayTime) {
        return Math.floorDiv(dayTime - NIGHT_START, DAY_LENGTH);
    }

    /** Called every server tick by {@link KingdomVillagers#tick}. Looks once a second for nightfall. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long night = nightOf(server.overworld().getDayTime());
        KingdomData data = KingdomData.get(server);
        long last = data.lastNight();
        if (last == night) {
            return;
        }
        data.setLastNight(night);
        // The first check in a world, or time set backwards with /time: no arrivals, just note the night.
        if (last == KingdomData.NO_NIGHT || night < last) {
            return;
        }
        for (Kingdom kingdom : List.copyOf(data.all())) {
            nightfall(server, kingdom);
        }
    }

    /**
     * Night falls on one kingdom: if it has a working Innkeeper, a Town Hall and a free home, a villager moves in.
     *
     * @return the villager who moved in, if one did
     */
    public static Optional<Villager> nightfall(MinecraftServer server, Kingdom kingdom) {
        if (!hasInnkeeper(server, kingdom)) {
            return Optional.empty();
        }
        return KingdomVillagers.moveIn(server, kingdom, "kingdoms.kingdom.villager_arrived_night");
    }

    /** Whether any Innkeeper is working for the kingdom right now. */
    public static boolean hasInnkeeper(MinecraftServer server, Kingdom kingdom) {
        KingdomData data = KingdomData.get(server);
        for (UUID resident : kingdom.residents()) {
            Villager villager = findVillager(server, resident);
            if (villager != null && isInnkeeperFor(data, villager, kingdom)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInnkeeperFor(KingdomData data, Villager villager, Kingdom kingdom) {
        if (villager.getVillagerData().getProfession() != ModVillagers.INNKEEPER_PROFESSION.get()) {
            return false;
        }
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !(villager.level() instanceof ServerLevel world) || !site.get().dimension().equals(world.dimension())
                || !world.isLoaded(site.get().pos()) || !world.getBlockState(site.get().pos()).is(ModBlocks.TAVERN.get())) {
            return false;
        }
        return data.claimedBy(ChunkClaim.of(site.get())).filter(owner -> owner == kingdom).isPresent();
    }

    private static Villager findVillager(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    /** A player right-clicked a Tavern: tell them whether it's drawing travellers into their kingdom. */
    public static void onTavernUsed(ServerPlayer player, BlockPos pos) {
        KingdomData data = KingdomData.get(player.server);
        Optional<Kingdom> owner = data.claimedBy(ChunkClaim.of(GlobalPos.of(player.level().dimension(), pos)));
        Optional<Kingdom> own = Kingdoms.ofPlayer(player);
        String key;
        if (own.isEmpty() || owner.isEmpty() || owner.get() != own.get()) {
            key = "kingdoms.tavern.not_on_land";
        } else if (hasInnkeeper(player.server, own.get())) {
            key = "kingdoms.tavern.open";
        } else {
            key = "kingdoms.tavern.no_innkeeper";
        }
        player.displayClientMessage(Component.translatable(key), true);
    }
}
