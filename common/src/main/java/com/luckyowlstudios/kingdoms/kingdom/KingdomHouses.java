package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Houses: how many villagers a kingdom has room for. Players build a house and put a House Marker in it; the marker
 * scans the space it's in (see {@link HouseScan}) and, if it's a proper home on a kingdom's land, every bed in it is a
 * home for one of that kingdom's villagers. Villagers only move in (see {@link KingdomVillagers}) while there's a free
 * home, and each one is given a bed in its house to sleep in.
 * <p>
 * Houses are scanned when a marker is placed or right-clicked (the player is told what's wrong, if anything), again
 * before anyone moves in, and every {@link #RESCAN_SECONDS} seconds while loaded, so a house that's changed since is
 * noticed. A villager whose house stops being a home keeps it (and gets it back once it's fixed); one whose house is
 * taken down is homeless, and gets the next free home before any newcomer. Server side only.
 */
public final class KingdomHouses {

    /** How often a loaded house is scanned again, in seconds. */
    public static final int RESCAN_SECONDS = 30;
    /** The most problem spots shown with particles at once. */
    private static final int MAX_MARKED_SPOTS = 24;

    private KingdomHouses() {
    }

    // ---- Reading ----

    /** The house whose marker is at {@code pos}, if there is one. */
    public static Optional<House> at(MinecraftServer server, GlobalPos pos) {
        return KingdomData.get(server).house(pos);
    }

    /** The kingdom a house belongs to: the one whose land its marker is on. */
    public static Optional<Kingdom> owner(MinecraftServer server, House house) {
        return KingdomData.get(server).claimedBy(ChunkClaim.of(house.pos()));
    }

    /** Every house on the kingdom's land, valid or not, in the order they were placed. */
    public static List<House> of(MinecraftServer server, Kingdom kingdom) {
        KingdomData data = KingdomData.get(server);
        return data.houses().stream().filter(house -> data.claimedBy(ChunkClaim.of(house.pos())).filter(owner -> owner == kingdom).isPresent())
                .toList();
    }

    /** How many villagers the kingdom has homes for: a bed each in every valid house on its land. */
    public static int homes(MinecraftServer server, Kingdom kingdom) {
        return of(server, kingdom).stream().mapToInt(House::homes).sum();
    }

    // ---- Scanning ----

    /**
     * Scans a house again and notes what it found: whether it's a home, and its beds. Villagers who no longer belong
     * to the house's kingdom stop living there, and so do any past the number of beds.
     */
    public static HouseScan.Result rescan(ServerLevel world, House house) {
        KingdomData data = KingdomData.get(world.getServer());
        HouseScan.Result scan = HouseScan.scan(world, house.pos().pos());
        Optional<Kingdom> owner = owner(world.getServer(), house);
        boolean valid = scan.valid() && owner.isPresent() && duplicateOf(data, house, scan).isEmpty();
        // While the house is open (a wall knocked out to build on, say), keep its beds as they were.
        data.updateHouse(house, valid, scan.enclosed() ? scan.beds() : house.beds());
        data.keepOccupants(house, villager -> owner.map(kingdom -> kingdom.residents().contains(villager)).orElse(false),
                house.beds().size());
        if (valid) {
            for (int i = 0; i < house.occupants().size(); i++) {
                if (world.getEntity(house.occupants().get(i)) instanceof Villager villager) {
                    giveBed(world, villager, house.beds().get(i));
                }
            }
        }
        return scan;
    }

    /** Another marker in the same house that was placed first, if there is one: only one marker counts per house. */
    private static Optional<BlockPos> duplicateOf(KingdomData data, House house, HouseScan.Result scan) {
        for (BlockPos other : scan.markers()) {
            Optional<House> first = data.house(GlobalPos.of(house.pos().dimension(), other));
            if (first.isPresent() && data.placedBefore(first.get(), house)) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    /** Called every server tick by {@link KingdomVillagers#tick}. Scans each loaded house now and then. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long second = server.getTickCount() / 20;
        KingdomData data = KingdomData.get(server);
        for (House house : List.copyOf(data.houses())) {
            // Spread the houses out over the rescan time, so they're not all scanned on the same tick.
            if (Math.floorMod(house.pos().pos().asLong() + second, RESCAN_SECONDS) != 0) {
                continue;
            }
            ServerLevel world = server.getLevel(house.pos().dimension());
            BlockPos pos = house.pos().pos();
            if (world == null || !world.isLoaded(pos)) {
                continue;
            }
            if (!world.getBlockState(pos).is(ModBlocks.HOUSE_MARKER.get())) {
                data.removeHouse(house.pos()); // gone without us hearing about it
                continue;
            }
            boolean wasValid = house.valid();
            HouseScan.Result scan = rescan(world, house);
            if (wasValid && !house.valid() && !house.occupants().isEmpty()) {
                List<Component> problems = problems(server, house, scan);
                owner(server, house).ifPresent(kingdom -> Kingdoms.broadcast(server, kingdom, Component.translatable(
                        "kingdoms.house.broken", kingdom.displayName(), pos.getX(), pos.getY(), pos.getZ(),
                        problems.isEmpty() ? Component.empty() : problems.get(0)).withStyle(ChatFormatting.GOLD)));
            }
        }
        if (second % 5 == 0) {
            for (Kingdom kingdom : List.copyOf(data.all())) {
                settle(server, kingdom);
            }
        }
    }

    // ---- Villagers ----

    /**
     * Gives the kingdom's homeless villagers (ones who joined with nowhere to live, or whose house was taken down) the
     * free homes it has, before any newcomer can have them.
     */
    public static void settle(MinecraftServer server, Kingdom kingdom) {
        KingdomData data = KingdomData.get(server);
        Set<UUID> housed = new HashSet<>();
        data.houses().forEach(house -> housed.addAll(house.occupants()));
        List<UUID> homeless = new ArrayList<>(kingdom.residents().stream().filter(villager -> !housed.contains(villager)).toList());
        if (homeless.isEmpty()) {
            return;
        }
        for (House house : of(server, kingdom)) {
            while (house.hasRoom() && !homeless.isEmpty()) {
                UUID villager = homeless.remove(0);
                data.addOccupant(house, villager);
                ServerLevel world = server.getLevel(house.pos().dimension());
                if (world != null && world.getEntity(villager) instanceof Villager loaded) {
                    giveBed(world, loaded, house.beds().get(house.occupants().size() - 1));
                }
            }
        }
    }

    /** A villager who just joined the kingdom moves into {@code house}, which has room, and gets a bed there. */
    static void moveInto(ServerLevel world, House house, Villager villager) {
        KingdomData.get(world.getServer()).addOccupant(house, villager.getUUID());
        giveBed(world, villager, house.beds().get(house.occupants().size() - 1));
    }

    /** Makes {@code bed} the villager's home, the bed it goes to sleep in, and keeps other villagers from taking it. */
    private static void giveBed(ServerLevel world, Villager villager, BlockPos bed) {
        GlobalPos home = GlobalPos.of(world.dimension(), bed);
        Optional<GlobalPos> current = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (current.filter(home::equals).isPresent()) {
            return;
        }
        // Let go of the bed it had before, so another villager can have it.
        current.ifPresent(old -> {
            ServerLevel oldWorld = world.getServer().getLevel(old.dimension());
            if (oldWorld != null && oldWorld.isLoaded(old.pos()) && oldWorld.getPoiManager().getType(old.pos()).isPresent()) {
                oldWorld.getPoiManager().release(old.pos());
            }
        });
        // Beds are points of interest with one ticket: take it, like a villager claiming a bed does.
        world.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1);
        villager.getBrain().setMemory(MemoryModuleType.HOME, home);
    }

    // ---- The House Marker block ----

    /** A House Marker was put down (by anyone, or anything). It's checked when it's scanned. */
    public static void onPlaced(ServerLevel world, BlockPos pos) {
        KingdomData.get(world.getServer()).addHouse(GlobalPos.of(world.dimension(), pos));
    }

    /** A House Marker was removed. Anyone who lived there is homeless until another home turns up. */
    public static void onRemoved(ServerLevel world, BlockPos pos) {
        MinecraftServer server = world.getServer();
        KingdomData.get(server).removeHouse(GlobalPos.of(world.dimension(), pos)).ifPresent(house -> {
            if (!house.occupants().isEmpty()) {
                owner(server, house).ifPresent(kingdom -> {
                    Kingdoms.broadcast(server, kingdom, Component.translatable("kingdoms.house.removed", kingdom.displayName(),
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD));
                    settle(server, kingdom);
                });
            }
        });
    }

    /**
     * A player placed or right-clicked a House Marker: scans the house and tells them whether it's a home, and if not,
     * what's wrong. Problem spots (dark corners, a hole in the roof) are marked with particles only they can see.
     */
    public static void report(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.serverLevel();
        GlobalPos at = GlobalPos.of(world.dimension(), pos);
        KingdomData data = KingdomData.get(player.server);
        House house = data.house(at).orElseGet(() -> data.addHouse(at));
        HouseScan.Result scan = rescan(world, house);
        List<Component> problems = problems(player.server, house, scan);
        if (problems.isEmpty()) {
            int beds = house.beds().size();
            player.sendSystemMessage(Component.translatable(beds == 1 ? "kingdoms.house.ready.one" : "kingdoms.house.ready.many", beds)
                    .withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(house.occupants().isEmpty()
                    ? Component.translatable("kingdoms.house.vacant").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("kingdoms.house.occupied", house.occupants().size(), beds).withStyle(ChatFormatting.GRAY));
            mark(player, ParticleTypes.HAPPY_VILLAGER, pos);
            house.beds().forEach(bed -> mark(player, ParticleTypes.HAPPY_VILLAGER, bed));
            return;
        }
        player.sendSystemMessage(Component.translatable("kingdoms.house.not_ready").withStyle(ChatFormatting.GOLD));
        problems.forEach(player::sendSystemMessage);
        if (scan.leak() != null && scan.opening() == HouseScan.Opening.SKY) {
            mark(player, ParticleTypes.ANGRY_VILLAGER, scan.leak());
        }
        scan.darkSpots().stream().limit(MAX_MARKED_SPOTS).forEach(spot -> mark(player, ParticleTypes.ANGRY_VILLAGER, spot));
    }

    /** Why a house isn't a home, one line each, or nothing if it is. */
    public static List<Component> problems(MinecraftServer server, House house, HouseScan.Result scan) {
        List<Component> problems = new ArrayList<>();
        KingdomData data = KingdomData.get(server);
        if (owner(server, house).isEmpty()) {
            problems.add(line("kingdoms.house.problem.no_land"));
        }
        switch (scan.opening()) {
            case SKY -> {
                BlockPos leak = scan.leak() == null ? house.pos().pos() : scan.leak();
                problems.add(line("kingdoms.house.problem.sky", leak.getX(), leak.getY(), leak.getZ()));
            }
            case TOO_BIG -> problems.add(line("kingdoms.house.problem.too_big", HouseScan.MAX_SPACE, HouseScan.MAX_REACH));
            case UNLOADED -> problems.add(line("kingdoms.house.problem.unloaded"));
            case NONE -> {
                if (scan.beds().isEmpty()) {
                    problems.add(line("kingdoms.house.problem.no_bed"));
                }
                if (scan.exteriorDoors() == 0) {
                    problems.add(line("kingdoms.house.problem.no_door"));
                }
                if (!scan.darkSpots().isEmpty()) {
                    BlockPos nearest = scan.darkSpots().get(0);
                    int count = scan.darkSpots().size();
                    problems.add(line(count == 1 ? "kingdoms.house.problem.dark.one" : "kingdoms.house.problem.dark.many",
                            count, nearest.getX(), nearest.getY(), nearest.getZ()));
                }
                duplicateOf(data, house, scan).ifPresent(other ->
                        problems.add(line("kingdoms.house.problem.duplicate", other.getX(), other.getY(), other.getZ())));
            }
        }
        return problems;
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.literal(" - ").append(Component.translatable(key, args)).withStyle(ChatFormatting.GRAY);
    }

    private static void mark(ServerPlayer player, ParticleOptions particle, BlockPos pos) {
        player.serverLevel().sendParticles(player, particle, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                4, 0.25, 0.25, 0.25, 0.0);
    }
}
