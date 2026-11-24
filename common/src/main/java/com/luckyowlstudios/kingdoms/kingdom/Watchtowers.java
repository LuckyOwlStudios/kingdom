package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.mixin.PatrollingMonsterAccessor;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Watchtower and its Lookout: a kingdom's early warning.
 * <p>
 * A Lookout counts as on watch when it lives in the kingdom (a resident), is loaded, and its job site is a Watchtower
 * on the kingdom's land. Lookouts keep watch day and night. Every couple of seconds each manned Watchtower looks
 * {@link #RANGE} blocks around itself, and for each new {@link Sighting} it tells every online member in chat, with
 * the coordinates, coloured by how much it matters ({@link AlertLevel}).
 * <p>
 * Each thing is reported once per kingdom: whatever was spotted gets an entity tag, so it isn't reported again by
 * the same kingdom's other towers, or after a restart. A group (like the illagers of one patrol) is one alert.
 * <p>
 * To watch for something new, add one {@link #register} line below and its {@code kingdoms.watchtower.alert.<id>}
 * language key. Server side only.
 */
public final class Watchtowers {

    /** How far a Lookout can see from its Watchtower, in blocks, sideways. */
    public static final int RANGE = 64;
    /** And up and down. */
    public static final int VERTICAL_RANGE = 48;
    /** Ticks between looks. */
    private static final int INTERVAL = 40;
    private static final String SPOTTED_TAG = "kingdoms.spotted.";

    /** How much an alert matters, which sets its colour in chat. */
    public enum AlertLevel {
        /** Good to know, like a wandering trader turning up. */
        INFO(ChatFormatting.BLUE),
        /** Worth keeping an eye on. */
        CAUTION(ChatFormatting.GOLD),
        /** Danger, like an illager patrol. */
        THREAT(ChatFormatting.RED);

        private final ChatFormatting color;

        AlertLevel(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting color() {
            return this.color;
        }
    }

    /**
     * Something a Lookout watches for.
     *
     * @param id          names its language key, {@code kingdoms.watchtower.alert.<id>}, which gets the number seen
     *                    and the x, y and z of where
     * @param level       how much it matters
     * @param matches     which entities count
     * @param groupRadius matching entities this close to the first one seen are reported with it, as one alert;
     *                    0 for one alert per entity
     */
    public record Sighting(String id, AlertLevel level, Predicate<Entity> matches, double groupRadius) {

        public String translationKey() {
            return "kingdoms.watchtower.alert." + this.id;
        }
    }

    /**
     * One alert sent to a kingdom.
     *
     * @param sighting what was seen
     * @param count    how many
     * @param pos      where (the first one seen)
     */
    public record Alert(Sighting sighting, int count, BlockPos pos) {

        public Component message() {
            return Component.translatable(this.sighting.translationKey(), this.count, this.pos.getX(), this.pos.getY(), this.pos.getZ())
                    .withStyle(this.sighting.level().color());
        }
    }

    private static final List<Sighting> SIGHTINGS = new ArrayList<>();

    public static final Sighting WANDERING_TRADER = register(new Sighting("wandering_trader", AlertLevel.INFO,
            entity -> entity instanceof WanderingTrader, 0));

    /** Patrols only: not illagers guarding an outpost, or ones in a raid (the raid bar already says so). */
    public static final Sighting ILLAGER_PATROL = register(new Sighting("illager_patrol", AlertLevel.THREAT,
            entity -> entity instanceof PatrollingMonster monster && ((PatrollingMonsterAccessor) monster).kingdoms$isPatrolling(), 32));

    private Watchtowers() {
    }

    public static Sighting register(Sighting sighting) {
        SIGHTINGS.add(sighting);
        return sighting;
    }

    public static List<Sighting> sightings() {
        return Collections.unmodifiableList(SIGHTINGS);
    }

    /** Called every server tick. Every couple of seconds, every manned Watchtower takes a look around. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        for (Kingdom kingdom : List.copyOf(KingdomData.get(server).all())) {
            for (Alert alert : look(server, kingdom)) {
                Kingdoms.broadcast(server, kingdom, alert.message());
            }
        }
    }

    /**
     * Every Watchtower of the kingdom with a Lookout on watch looks around once. Anything new it sees is marked as
     * reported. Doesn't send anything: see {@link #tick}.
     *
     * @return the alerts for the kingdom, in the order seen
     */
    public static List<Alert> look(MinecraftServer server, Kingdom kingdom) {
        List<Alert> alerts = new ArrayList<>();
        for (GlobalPos tower : mannedTowers(server, kingdom)) {
            ServerLevel world = server.getLevel(tower.dimension());
            if (world != null) {
                lookFrom(world, tower.pos(), kingdom, alerts);
            }
        }
        return alerts;
    }

    private static void lookFrom(ServerLevel world, BlockPos tower, Kingdom kingdom, List<Alert> alerts) {
        String tag = SPOTTED_TAG + kingdom.id();
        AABB area = new AABB(tower).inflate(RANGE, VERTICAL_RANGE, RANGE);
        List<Entity> seen = world.getEntities((Entity) null, area, entity -> entity.isAlive() && !entity.getTags().contains(tag));
        for (Sighting sighting : SIGHTINGS) {
            List<Entity> matching = seen.stream().filter(sighting.matches()).toList();
            for (Entity first : matching) {
                if (first.getTags().contains(tag)) {
                    continue; // already reported with an earlier one of its group
                }
                int count = 0;
                for (Entity other : matching) {
                    if (other == first || (sighting.groupRadius() > 0 && !other.getTags().contains(tag)
                            && other.distanceToSqr(first) <= sighting.groupRadius() * sighting.groupRadius())) {
                        other.addTag(tag);
                        count++;
                    }
                }
                alerts.add(new Alert(sighting, count, first.blockPosition()));
            }
        }
    }

    /** The Watchtowers on the kingdom's land with at least one of its Lookouts on watch. */
    public static Set<GlobalPos> mannedTowers(MinecraftServer server, Kingdom kingdom) {
        KingdomData data = KingdomData.get(server);
        Set<GlobalPos> towers = new LinkedHashSet<>();
        for (UUID resident : kingdom.residents()) {
            Villager villager = findVillager(server, resident);
            if (villager != null) {
                onWatch(data, villager, kingdom).ifPresent(towers::add);
            }
        }
        return towers;
    }

    /** The Watchtower the villager keeps watch from for the kingdom, if it's a Lookout on watch. */
    private static Optional<GlobalPos> onWatch(KingdomData data, Villager villager, Kingdom kingdom) {
        if (villager.getVillagerData().getProfession() != ModVillagers.LOOKOUT_PROFESSION.get()
                || !(villager.level() instanceof ServerLevel world)) {
            return Optional.empty();
        }
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(world.dimension()) || !world.isLoaded(site.get().pos())
                || !world.getBlockState(site.get().pos()).is(ModBlocks.WATCHTOWER.get())
                || data.claimedBy(ChunkClaim.of(site.get())).filter(owner -> owner == kingdom).isEmpty()) {
            return Optional.empty();
        }
        return site;
    }

    private static Villager findVillager(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    /** A player right-clicked a Watchtower: say whose it is and whether a Lookout keeps watch from it. */
    public static void onTowerUsed(ServerPlayer player, BlockPos pos) {
        GlobalPos tower = GlobalPos.of(player.level().dimension(), pos);
        Optional<Kingdom> owner = Kingdoms.claimedBy(player.server, tower);
        if (owner.isEmpty()) {
            player.displayClientMessage(Component.translatable("kingdoms.watchtower.unclaimed"), true);
        } else if (mannedTowers(player.server, owner.get()).contains(tower)) {
            player.displayClientMessage(Component.translatable("kingdoms.watchtower.manned", owner.get().displayName()), true);
        } else {
            player.displayClientMessage(Component.translatable("kingdoms.watchtower.unmanned", owner.get().displayName()), true);
        }
    }
}
