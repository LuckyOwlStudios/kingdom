package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.network.ResearchActionPayload;
import com.luckyowlstudios.kingdoms.network.ResearchViewPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Researching perks at the Scholar's Desk.
 * <p>
 * The leader picks a perk tier at a desk on the kingdom's land. Its perk points are spent straight away and the
 * research starts; a kingdom researches one tier at a time. Every second, each Scholar working for the kingdom adds a
 * second of work, so a tier that takes one Scholar 10 minutes takes two Scholars 5. With no Scholars at work the
 * research waits. When it's done, the tier is unlocked with the usual toast.
 * <p>
 * A Scholar counts when it lives in the kingdom (a resident), is loaded, and its job site is a Scholar's Desk on the
 * kingdom's land. Server side only.
 */
public final class KingdomResearch {

    /** How close a player has to stay to the desk for its screen to keep working, in blocks. */
    private static final double DESK_RANGE = 8.0;

    /**
     * A perk tier being researched.
     *
     * @param perk     the perk
     * @param tier     the tier it will reach
     * @param progress seconds of Scholar work done so far
     * @param duration seconds of Scholar work it takes
     */
    public record Research(ResourceLocation perk, int tier, int progress, int duration) {

        public static final Codec<Research> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("perk").forGetter(Research::perk),
                Codec.INT.fieldOf("tier").forGetter(Research::tier),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Research::progress),
                Codec.INT.fieldOf("duration").forGetter(Research::duration)
        ).apply(instance, Research::new));

        public boolean isDone() {
            return this.progress >= this.duration;
        }

        Research advance(int work) {
            return new Research(this.perk, this.tier, Math.min(this.duration, this.progress + work), this.duration);
        }
    }

    /** Players with a desk's Research screen open, and which desk, so the screen can be kept up to date. */
    private static final Map<UUID, GlobalPos> VIEWERS = new HashMap<>();

    private KingdomResearch() {
    }

    /** Starts researching a tier. The caller has checked it can be researched; its points count as spent from now. */
    static void start(MinecraftServer server, Kingdom kingdom, KingdomPerk perk, int tier) {
        KingdomData.get(server).setResearch(kingdom, new Research(perk.id(), tier, 0, Math.max(1, perk.tier(tier).researchSeconds())));
        Kingdoms.broadcast(server, kingdom, Component.translatable("kingdoms.research.started", kingdom.displayName(),
                perk.displayName(), tier));
        refreshViewers(server, kingdom);
    }

    /** Called every server tick. Once a second, every kingdom's Scholars put in a second of work each. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        KingdomData data = KingdomData.get(server);
        for (Kingdom kingdom : List.copyOf(data.all())) {
            Optional<Research> research = kingdom.research();
            if (research.isEmpty()) {
                continue;
            }
            int scholars = workingScholars(server, kingdom);
            if (scholars > 0) {
                work(server, kingdom, scholars);
            }
        }
        refreshAllViewers(server);
    }

    /**
     * Adds {@code seconds} of Scholar work to the kingdom's research, and finishes it if that's enough.
     *
     * @return true if the research finished
     */
    public static boolean work(MinecraftServer server, Kingdom kingdom, int seconds) {
        Optional<Research> current = kingdom.research();
        if (current.isEmpty()) {
            return false;
        }
        Research research = current.get().advance(seconds);
        if (!research.isDone()) {
            KingdomData.get(server).setResearch(kingdom, research);
            return false;
        }
        finish(server, kingdom);
        return true;
    }

    /** Finishes the kingdom's research now, whatever its progress: the tier is unlocked. Used by commands and tests. */
    public static boolean finish(MinecraftServer server, Kingdom kingdom) {
        Optional<Research> current = kingdom.research();
        if (current.isEmpty()) {
            return false;
        }
        Research research = current.get();
        KingdomData.get(server).setResearch(kingdom, null);
        Optional<KingdomPerk> perk = KingdomPerks.byId(research.perk());
        // A perk that was removed from the mod since: nothing to unlock, and its points are free again.
        if (perk.isPresent() && research.tier() > kingdom.perkTier(research.perk()) && research.tier() <= perk.get().maxTier()) {
            KingdomPerks.grant(server, kingdom, perk.get(), research.tier());
        }
        refreshViewers(server, kingdom);
        return true;
    }

    /** The leader stopped the research. Its points come back, and the work done so far is lost. */
    public static boolean cancel(ServerPlayer player) {
        Optional<Kingdom> found = Kingdoms.ofPlayer(player);
        if (found.isEmpty() || found.get().research().isEmpty()) {
            return false;
        }
        Kingdom kingdom = found.get();
        if (!kingdom.rank(player.getUUID()).atLeast(KingdomPerks.UNLOCK_RANK)) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_leader", kingdom.displayName()));
            return false;
        }
        Research research = kingdom.research().get();
        KingdomData.get(player.server).setResearch(kingdom, null);
        Component name = KingdomPerks.byId(research.perk()).map(KingdomPerk::displayName).orElse(Component.literal(research.perk().toString()));
        Kingdoms.broadcast(player.server, kingdom, Component.translatable("kingdoms.research.cancelled", kingdom.displayName(), name));
        refreshViewers(player.server, kingdom);
        return true;
    }

    /**
     * How many Scholars are working for the kingdom right now: residents, loaded, with the Scholar job, whose job site
     * is a Scholar's Desk on the kingdom's land.
     */
    public static int workingScholars(MinecraftServer server, Kingdom kingdom) {
        KingdomData data = KingdomData.get(server);
        int count = 0;
        for (UUID resident : kingdom.residents()) {
            Villager villager = findVillager(server, resident);
            if (villager != null && isWorkingFor(data, villager, kingdom)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWorkingFor(KingdomData data, Villager villager, Kingdom kingdom) {
        if (villager.isSleeping() || villager.getVillagerData().getProfession() != ModVillagers.SCHOLAR_PROFESSION.get()) {
            return false;
        }
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !(villager.level() instanceof ServerLevel world) || !site.get().dimension().equals(world.dimension())
                || !world.isLoaded(site.get().pos()) || !world.getBlockState(site.get().pos()).is(ModBlocks.SCHOLARS_DESK.get())) {
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

    // ---- The desk's screen ----

    /** A player right-clicked a Scholar's Desk. Members on their own kingdom's land get the Research screen. */
    public static void onDeskUsed(ServerPlayer player, BlockPos pos) {
        Optional<Kingdom> own = Kingdoms.ofPlayer(player);
        if (own.isEmpty()) {
            player.displayClientMessage(Component.translatable("kingdoms.research.desk.not_in_kingdom"), true);
            return;
        }
        GlobalPos desk = GlobalPos.of(player.level().dimension(), pos);
        Optional<Kingdom> landOwner = Kingdoms.claimedBy(player.server, desk);
        if (landOwner.isEmpty() || landOwner.get() != own.get()) {
            player.displayClientMessage(Component.translatable("kingdoms.research.desk.outside_land", own.get().displayName()), true);
            return;
        }
        VIEWERS.put(player.getUUID(), desk);
        player.connection.send(new ClientboundCustomPayloadPacket(view(player, own.get(), pos)));
    }

    /** A button in the Research screen, or the screen closing. */
    public static void handle(ServerPlayer player, ResearchActionPayload action) {
        if (action.action() == ResearchActionPayload.Action.CLOSE) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        GlobalPos desk = GlobalPos.of(player.level().dimension(), action.desk());
        Optional<Kingdom> kingdom = Kingdoms.ofPlayer(player);
        if (kingdom.isEmpty() || !canUseDesk(player, kingdom.get(), desk)) {
            return;
        }
        switch (action.action()) {
            case START -> KingdomPerks.upgrade(player, action.perk());
            case CANCEL -> cancel(player);
            default -> {
            }
        }
        Kingdoms.ofPlayer(player).ifPresent(current -> player.connection.send(new ClientboundCustomPayloadPacket(view(player, current, action.desk()))));
    }

    /** The player is next to a Scholar's Desk on their kingdom's land. */
    private static boolean canUseDesk(ServerPlayer player, Kingdom kingdom, GlobalPos desk) {
        if (!desk.dimension().equals(player.level().dimension())
                || player.distanceToSqr(desk.pos().getCenter()) > DESK_RANGE * DESK_RANGE
                || !player.level().getBlockState(desk.pos()).is(ModBlocks.SCHOLARS_DESK.get())) {
            return false;
        }
        return Kingdoms.claimedBy(player.server, desk).filter(owner -> owner == kingdom).isPresent();
    }

    public static ResearchViewPayload view(ServerPlayer viewer, Kingdom kingdom, BlockPos desk) {
        Optional<ResearchViewPayload.Progress> progress = kingdom.research()
                .map(research -> new ResearchViewPayload.Progress(research.perk(), research.tier(), research.progress(), research.duration()));
        return new ResearchViewPayload(desk, kingdom.name(), kingdom.color(), kingdom.rank(viewer.getUUID()), kingdom.level(),
                KingdomPerks.availablePoints(kingdom), Map.copyOf(kingdom.perks()), progress, workingScholars(viewer.server, kingdom));
    }

    /** Sends the latest to everyone in this kingdom with a desk screen open. */
    private static void refreshViewers(MinecraftServer server, Kingdom kingdom) {
        for (Map.Entry<UUID, GlobalPos> entry : List.copyOf(VIEWERS.entrySet())) {
            if (kingdom.isMember(entry.getKey())) {
                refresh(server, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void refreshAllViewers(MinecraftServer server) {
        for (Map.Entry<UUID, GlobalPos> entry : List.copyOf(VIEWERS.entrySet())) {
            refresh(server, entry.getKey(), entry.getValue());
        }
    }

    private static void refresh(MinecraftServer server, UUID id, GlobalPos desk) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        Optional<Kingdom> kingdom = player == null ? Optional.empty() : Kingdoms.ofPlayer(player);
        if (player == null || kingdom.isEmpty() || !canUseDesk(player, kingdom.get(), desk)) {
            // Gone, out of the kingdom, or walked away: stop sending.
            VIEWERS.remove(id);
            return;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(view(player, kingdom.get(), desk.pos())));
    }
}
