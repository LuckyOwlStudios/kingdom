package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.serialization.Codec;
import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every kingdom in the world, saved in the overworld's data ({@code data/kingdoms.dat}).
 */
public final class KingdomData extends SavedData {

    private static final String FILE_NAME = Constants.MOD_ID;
    private static final Codec<java.util.List<Kingdom>> LIST_CODEC = Kingdom.CODEC.listOf();

    private static final SavedData.Factory<KingdomData> FACTORY =
            new SavedData.Factory<>(KingdomData::new, KingdomData::load, null);

    private final Map<UUID, Kingdom> kingdoms = new LinkedHashMap<>();
    /** Who owns each claimed chunk, for quick lookups. Rebuilt from the kingdoms on load. */
    private final Map<ChunkClaim, UUID> claimOwners = new HashMap<>();

    public static KingdomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public Collection<Kingdom> all() {
        return Collections.unmodifiableCollection(this.kingdoms.values());
    }

    public Optional<Kingdom> get(UUID id) {
        return Optional.ofNullable(this.kingdoms.get(id));
    }

    public Optional<Kingdom> ofPlayer(UUID player) {
        return this.kingdoms.values().stream().filter(kingdom -> kingdom.isMember(player)).findFirst();
    }

    public Optional<Kingdom> atTownHall(GlobalPos pos) {
        return this.kingdoms.values().stream().filter(kingdom -> kingdom.townHall().filter(pos::equals).isPresent()).findFirst();
    }

    /** The kingdom that has claimed this chunk, if any. */
    public Optional<Kingdom> claimedBy(ChunkClaim chunk) {
        return Optional.ofNullable(this.claimOwners.get(chunk)).map(this.kingdoms::get);
    }

    /** True if nobody has claimed the chunk, or {@code kingdom} already has. */
    public boolean canClaim(Kingdom kingdom, ChunkClaim chunk) {
        UUID owner = this.claimOwners.get(chunk);
        return owner == null || owner.equals(kingdom.id());
    }

    /**
     * Gives the chunk to the kingdom.
     *
     * @return false if another kingdom already holds it
     */
    boolean claim(Kingdom kingdom, ChunkClaim chunk) {
        if (!this.canClaim(kingdom, chunk)) {
            return false;
        }
        kingdom.mutableClaims().add(chunk);
        this.claimOwners.put(chunk, kingdom.id());
        this.setDirty();
        return true;
    }

    /** Lets go of a chunk the kingdom holds. */
    void unclaim(Kingdom kingdom, ChunkClaim chunk) {
        if (kingdom.mutableClaims().remove(chunk)) {
            this.claimOwners.remove(chunk, kingdom.id());
            this.setDirty();
        }
    }

    public boolean isNameTaken(String name) {
        return this.kingdoms.values().stream().anyMatch(kingdom -> kingdom.name().equalsIgnoreCase(name));
    }

    public Optional<Kingdom> byName(String name) {
        return this.kingdoms.values().stream().filter(kingdom -> kingdom.name().equalsIgnoreCase(name.strip())).findFirst();
    }

    Kingdom create(MinecraftServer server, String name, DyeColor color, UUID founder, GlobalPos townHall) {
        Set<UUID> members = new LinkedHashSet<>();
        members.add(founder);
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), name, color, founder, founder, members, townHall);
        this.kingdoms.put(kingdom.id(), kingdom);
        this.kingdoms.values().forEach(other -> other.mutableInvites().remove(founder));
        this.claim(kingdom, ChunkClaim.of(townHall));
        this.setDirty();
        KingdomTeams.sync(server, kingdom);
        TownHallBlockEntity.refresh(server, townHall);
        return kingdom;
    }

    void invite(Kingdom kingdom, UUID player) {
        kingdom.mutableInvites().add(player);
        this.setDirty();
    }

    void addMember(MinecraftServer server, Kingdom kingdom, UUID player) {
        kingdom.mutableMembers().add(player);
        // Joining one kingdom uses up every invitation.
        this.kingdoms.values().forEach(other -> other.mutableInvites().remove(player));
        this.setDirty();
        KingdomTeams.sync(server, kingdom);
    }

    /**
     * Takes a player out of their kingdom. If they led it, the longest-standing member takes over;
     * if nobody is left, the kingdom is disbanded and its Town Hall is free again.
     *
     * @return true if the kingdom was disbanded
     */
    boolean removeMember(MinecraftServer server, Kingdom kingdom, UUID player) {
        KingdomTeams.removePlayer(server, kingdom, player);
        kingdom.mutableMembers().remove(player);
        this.setDirty();
        if (kingdom.mutableMembers().isEmpty()) {
            this.kingdoms.remove(kingdom.id());
            // Its land is free again.
            kingdom.mutableClaims().forEach(chunk -> this.claimOwners.remove(chunk, kingdom.id()));
            kingdom.mutableClaims().clear();
            KingdomTeams.remove(server, kingdom);
            // Its Town Hall is free again: put the book away and take the colours down.
            kingdom.townHall().ifPresent(hall -> TownHallBlockEntity.refresh(server, hall));
            return true;
        }
        kingdom.mutableOfficers().remove(player);
        if (kingdom.isLeader(player)) {
            // Officers first, then whoever has been around longest.
            UUID next = kingdom.mutableMembers().stream().filter(kingdom.mutableOfficers()::contains).findFirst()
                    .orElse(kingdom.mutableMembers().iterator().next());
            kingdom.setLeader(next);
            kingdom.mutableOfficers().remove(next);
        }
        return false;
    }

    void setRank(Kingdom kingdom, UUID player, KingdomRank rank) {
        switch (rank) {
            case MEMBER -> kingdom.mutableOfficers().remove(player);
            case OFFICER -> kingdom.mutableOfficers().add(player);
            case LEADER -> {
                // Handing over: the old leader stays on as an officer.
                kingdom.mutableOfficers().add(kingdom.leader());
                kingdom.mutableOfficers().remove(player);
                kingdom.setLeader(player);
            }
            case NONE -> {
            }
        }
        this.setDirty();
    }

    void ban(MinecraftServer server, Kingdom kingdom, UUID player) {
        if (kingdom.isMember(player)) {
            this.removeMember(server, kingdom, player);
        }
        kingdom.mutableInvites().remove(player);
        kingdom.mutableBanned().add(player);
        this.setDirty();
    }

    void unban(Kingdom kingdom, UUID player) {
        kingdom.mutableBanned().remove(player);
        this.setDirty();
    }

    void setColor(MinecraftServer server, Kingdom kingdom, DyeColor color) {
        kingdom.setColor(color);
        this.setDirty();
        KingdomTeams.sync(server, kingdom);
        kingdom.townHall().ifPresent(hall -> TownHallBlockEntity.refresh(server, hall));
    }

    void setSettings(MinecraftServer server, Kingdom kingdom, Kingdom.Settings settings) {
        kingdom.setSettings(settings);
        this.setDirty();
        KingdomTeams.sync(server, kingdom);
    }

    void setMap(Kingdom kingdom, net.minecraft.world.level.saveddata.maps.MapId map) {
        kingdom.setMap(map);
        this.setDirty();
    }

    void setBanner(Kingdom kingdom, ItemStack banner) {
        kingdom.setBanner(banner);
        this.setDirty();
    }

    /** Makes this the kingdom's seat and claims the chunk it stands in. The caller checks the chunk is free. */
    void setTownHall(Kingdom kingdom, GlobalPos pos) {
        kingdom.setTownHall(pos);
        this.claim(kingdom, ChunkClaim.of(pos));
        this.setDirty();
    }

    /** The kingdom's Town Hall was broken: it keeps going, but needs a new one, and loses the hall's chunk. */
    void clearTownHall(Kingdom kingdom) {
        kingdom.townHall().ifPresent(hall -> this.unclaim(kingdom, ChunkClaim.of(hall)));
        kingdom.clearTownHall();
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), java.util.List.copyOf(this.kingdoms.values()))
                .resultOrPartial(error -> Constants.LOG.error("Couldn't save kingdoms: {}", error))
                .ifPresent(list -> tag.put("kingdoms", list));
        return tag;
    }

    private static KingdomData load(CompoundTag tag, HolderLookup.Provider registries) {
        KingdomData data = new KingdomData();
        if (tag.contains("kingdoms")) {
            LIST_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("kingdoms"))
                    .resultOrPartial(error -> Constants.LOG.error("Couldn't load kingdoms: {}", error))
                    .ifPresent(list -> list.forEach(kingdom -> data.kingdoms.put(kingdom.id(), kingdom)));
        }
        for (Kingdom kingdom : data.kingdoms.values()) {
            // Kingdoms founded before chunk claims existed get their Town Hall's chunk now.
            kingdom.townHall().map(ChunkClaim::of).filter(chunk -> !data.claimOwners.containsKey(chunk))
                    .ifPresent(kingdom.mutableClaims()::add);
            for (ChunkClaim chunk : kingdom.mutableClaims()) {
                data.claimOwners.putIfAbsent(chunk, kingdom.id());
            }
        }
        return data;
    }
}
