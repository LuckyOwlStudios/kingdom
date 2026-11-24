package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * How kingdoms stand with each other, saved in the overworld's data ({@code data/kingdoms_diplomacy.dat}).
 * <ul>
 *     <li>An alliance needs both sides: one kingdom asks, the other accepts. Either side can end it.</li>
 *     <li>Calling a kingdom an enemy is one-sided, and only the kingdom that declared it can take it back.</li>
 * </ul>
 * Kept apart from {@link KingdomData} so relations can grow without touching the kingdom format.
 * Changes go through {@link Diplomacy}, which checks ranks and tells everyone involved.
 */
public final class KingdomDiplomacy extends SavedData {

    private static final String FILE_NAME = Constants.MOD_ID + "_diplomacy";

    private static final SavedData.Factory<KingdomDiplomacy> FACTORY =
            new SavedData.Factory<>(KingdomDiplomacy::new, KingdomDiplomacy::load, null);

    /** One kingdom's stance towards another. For alliances the pair is stored in a fixed order. */
    record Link(UUID from, UUID to) {

        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("from").forGetter(Link::from),
                UUIDUtil.CODEC.fieldOf("to").forGetter(Link::to)
        ).apply(instance, Link::new));

        /** The same pair whichever way round it's given, for two-sided relations. */
        static Link pair(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new Link(a, b) : new Link(b, a);
        }

        boolean involves(UUID kingdom) {
            return this.from.equals(kingdom) || this.to.equals(kingdom);
        }
    }

    private record State(List<Link> alliances, List<Link> enemies, List<Link> requests) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Link.CODEC.listOf().optionalFieldOf("alliances", List.of()).forGetter(State::alliances),
                Link.CODEC.listOf().optionalFieldOf("enemies", List.of()).forGetter(State::enemies),
                Link.CODEC.listOf().optionalFieldOf("alliance_requests", List.of()).forGetter(State::requests)
        ).apply(instance, State::new));
    }

    private final Set<Link> alliances = new LinkedHashSet<>();
    /** {@code from} has declared {@code to} an enemy. */
    private final Set<Link> enemies = new LinkedHashSet<>();
    /** {@code from} has asked {@code to} for an alliance and is waiting for an answer. */
    private final Set<Link> requests = new LinkedHashSet<>();

    public static KingdomDiplomacy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    // ---- Queries ----

    public boolean areAllied(UUID a, UUID b) {
        return this.alliances.contains(Link.pair(a, b));
    }

    /** True if {@code kingdom} has declared {@code other} an enemy. */
    public boolean hasDeclaredEnemy(UUID kingdom, UUID other) {
        return this.enemies.contains(new Link(kingdom, other));
    }

    /** True if either kingdom has declared the other an enemy. */
    public boolean areHostile(UUID a, UUID b) {
        return this.hasDeclaredEnemy(a, b) || this.hasDeclaredEnemy(b, a);
    }

    /** True if {@code from} has asked {@code to} for an alliance that hasn't been answered yet. */
    public boolean hasRequested(UUID from, UUID to) {
        return this.requests.contains(new Link(from, to));
    }

    /** The kingdoms allied with this one. */
    public Set<UUID> alliesOf(UUID kingdom) {
        Set<UUID> allies = new LinkedHashSet<>();
        for (Link link : this.alliances) {
            if (link.involves(kingdom)) {
                allies.add(link.from().equals(kingdom) ? link.to() : link.from());
            }
        }
        return allies;
    }

    /** The kingdoms this one has declared enemies. */
    public Set<UUID> enemiesOf(UUID kingdom) {
        Set<UUID> declared = new LinkedHashSet<>();
        for (Link link : this.enemies) {
            if (link.from().equals(kingdom)) {
                declared.add(link.to());
            }
        }
        return declared;
    }

    // ---- Changes, used by Diplomacy ----

    void request(UUID from, UUID to) {
        if (this.requests.add(new Link(from, to))) {
            this.setDirty();
        }
    }

    boolean removeRequest(UUID from, UUID to) {
        boolean removed = this.requests.remove(new Link(from, to));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** Makes the two kingdoms allies. Any requests between them are used up and any enemy declarations dropped. */
    void ally(UUID a, UUID b) {
        this.alliances.add(Link.pair(a, b));
        this.requests.remove(new Link(a, b));
        this.requests.remove(new Link(b, a));
        this.enemies.remove(new Link(a, b));
        this.enemies.remove(new Link(b, a));
        this.setDirty();
    }

    boolean endAlliance(UUID a, UUID b) {
        boolean removed = this.alliances.remove(Link.pair(a, b));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** {@code from} calls {@code to} an enemy. That ends any alliance between them and any pending requests. */
    void declareEnemy(UUID from, UUID to) {
        this.alliances.remove(Link.pair(from, to));
        this.requests.remove(new Link(from, to));
        this.requests.remove(new Link(to, from));
        this.enemies.add(new Link(from, to));
        this.setDirty();
    }

    boolean makePeace(UUID from, UUID to) {
        boolean removed = this.enemies.remove(new Link(from, to));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** A kingdom was disbanded: it has no relations any more. */
    void forget(UUID kingdom) {
        boolean changed = this.alliances.removeIf(link -> link.involves(kingdom));
        changed |= this.enemies.removeIf(link -> link.involves(kingdom));
        changed |= this.requests.removeIf(link -> link.involves(kingdom));
        if (changed) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        State state = new State(List.copyOf(this.alliances), List.copyOf(this.enemies), List.copyOf(this.requests));
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .resultOrPartial(error -> Constants.LOG.error("Couldn't save kingdom diplomacy: {}", error))
                .ifPresent(encoded -> tag.put("diplomacy", encoded));
        return tag;
    }

    private static KingdomDiplomacy load(CompoundTag tag, HolderLookup.Provider registries) {
        KingdomDiplomacy diplomacy = new KingdomDiplomacy();
        if (tag.contains("diplomacy")) {
            State.CODEC.parse(NbtOps.INSTANCE, tag.get("diplomacy"))
                    .resultOrPartial(error -> Constants.LOG.error("Couldn't load kingdom diplomacy: {}", error))
                    .ifPresent(state -> {
                        diplomacy.alliances.addAll(state.alliances());
                        diplomacy.enemies.addAll(state.enemies());
                        diplomacy.requests.addAll(state.requests());
                    });
        }
        return diplomacy;
    }
}
