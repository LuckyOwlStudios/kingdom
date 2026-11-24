package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A player-founded kingdom. Works like a vanilla team (and is backed by one, so names show in its colour):
 * a leader, members, and invitations. Founded at a Town Hall, which becomes its seat.
 * <p>
 * Read it through {@link Kingdoms}; changes go through {@link Kingdoms} or {@link KingdomData} so they are saved
 * and the team stays in sync.
 */
public final class Kingdom {

    public static final int MAX_NAME_LENGTH = 32;

    public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Kingdom::id),
            Codec.STRING.fieldOf("name").forGetter(Kingdom::name),
            DyeColor.CODEC.fieldOf("color").forGetter(Kingdom::color),
            UUIDUtil.CODEC.fieldOf("founder").forGetter(Kingdom::founder),
            UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(kingdom -> Optional.of(kingdom.leader)),
            UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(kingdom -> List.copyOf(kingdom.members)),
            UUIDUtil.CODEC.listOf().optionalFieldOf("invites", List.of()).forGetter(kingdom -> List.copyOf(kingdom.invites)),
            GlobalPos.CODEC.optionalFieldOf("town_hall").forGetter(Kingdom::townHall),
            ItemStack.CODEC.optionalFieldOf("banner").forGetter(Kingdom::banner),
            UUIDUtil.CODEC.listOf().optionalFieldOf("officers", List.of()).forGetter(kingdom -> List.copyOf(kingdom.officers)),
            UUIDUtil.CODEC.listOf().optionalFieldOf("banned", List.of()).forGetter(kingdom -> List.copyOf(kingdom.banned)),
            Settings.CODEC.optionalFieldOf("settings", Settings.DEFAULT).forGetter(Kingdom::settings),
            KingdomMap.MAP_CODEC.forGetter(Kingdom::map),
            ChunkClaim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(kingdom -> List.copyOf(kingdom.claims)),
            Growth.MAP_CODEC.forGetter(kingdom -> new Growth(kingdom.level, List.copyOf(kingdom.residents), Map.copyOf(kingdom.perks), kingdom.taxes, kingdom.xp, kingdom.vault.getItems(), Optional.ofNullable(kingdom.research)))
    ).apply(instance, (id, name, color, founder, leader, members, invites, townHall, banner, officers, banned, settings, map, claims, growth) -> {
        Kingdom kingdom = new Kingdom(id, name, color, founder, leader.orElse(founder), new LinkedHashSet<>(members), townHall.orElse(null));
        kingdom.invites.addAll(invites);
        kingdom.banner = banner.orElse(ItemStack.EMPTY);
        kingdom.officers.addAll(officers);
        kingdom.banned.addAll(banned);
        kingdom.settings = settings;
        kingdom.map = map;
        kingdom.claims.addAll(claims);
        kingdom.level = Math.clamp(growth.level(), 1, KingdomLevel.MAX_LEVEL);
        kingdom.residents.addAll(growth.residents());
        growth.perks().forEach((perk, tier) -> {
            if (tier > 0) kingdom.perks.put(perk, tier);
        });
        kingdom.taxes = Math.max(0, growth.taxes());
        kingdom.xp = Math.max(0, growth.xp());
        for (int slot = 0; slot < Math.min(growth.vault().size(), kingdom.vault.getContainerSize()); slot++) {
            kingdom.vault.setItem(slot, growth.vault().get(slot));
        }
        kingdom.research = growth.research().orElse(null);
        return kingdom;
    }));

    /**
     * Level, villagers, perk tiers, the tax treasury and the shared vault, saved as top-level fields ({@code level},
     * {@code residents}, {@code perks}, {@code taxes}, {@code vault}, {@code research}) but grouped so the kingdom codec stays under the
     * 16-field limit. Add new saved fields here too.
     */
    private record Growth(int level, List<UUID> residents, Map<ResourceLocation, Integer> perks, int taxes, int xp, List<ItemStack> vault,
                          Optional<KingdomResearch.Research> research) {
        static final com.mojang.serialization.MapCodec<Growth> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("level", 1).forGetter(Growth::level),
                UUIDUtil.CODEC.listOf().optionalFieldOf("residents", List.of()).forGetter(Growth::residents),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("perks", Map.of()).forGetter(Growth::perks),
                Codec.INT.optionalFieldOf("taxes", 0).forGetter(Growth::taxes),
                Codec.INT.optionalFieldOf("xp", 0).forGetter(Growth::xp),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("vault", List.of()).forGetter(Growth::vault),
                KingdomResearch.Research.CODEC.optionalFieldOf("research").forGetter(Growth::research)
        ).apply(instance, Growth::new));
    }

    /**
     * Preferences the leader can change.
     *
     * @param inviteOnly   players need an invitation to join; otherwise anyone who isn't banned can
     * @param friendlyFire members can hurt each other
     */
    public record Settings(boolean inviteOnly, boolean friendlyFire) {

        public static final Settings DEFAULT = new Settings(true, false);

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("invite_only", true).forGetter(Settings::inviteOnly),
                Codec.BOOL.optionalFieldOf("friendly_fire", false).forGetter(Settings::friendlyFire)
        ).apply(instance, Settings::new));
    }

    private final UUID id;
    private final String name;
    private DyeColor color;
    private final UUID founder;
    private UUID leader;
    private final Set<UUID> members;
    private final Set<UUID> invites = new HashSet<>();
    private GlobalPos townHall;
    private ItemStack banner = ItemStack.EMPTY;
    private final Set<UUID> officers = new HashSet<>();
    private final Set<UUID> banned = new HashSet<>();
    private Settings settings = Settings.DEFAULT;
    private KingdomMap map = KingdomMap.EMPTY;
    private final Set<ChunkClaim> claims = new LinkedHashSet<>();
    private int level = 1;
    private final Set<UUID> residents = new LinkedHashSet<>();
    private final Map<ResourceLocation, Integer> perks = new LinkedHashMap<>();
    private int taxes;
    private int xp;
    private final net.minecraft.world.SimpleContainer vault = new net.minecraft.world.SimpleContainer(SharedVault.SIZE);
    @org.jetbrains.annotations.Nullable
    private KingdomResearch.Research research;

    Kingdom(UUID id, String name, DyeColor color, UUID founder, UUID leader, Set<UUID> members, GlobalPos townHall) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.founder = founder;
        this.leader = leader;
        this.members = members;
        this.townHall = townHall;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public DyeColor color() {
        return this.color;
    }

    public UUID founder() {
        return this.founder;
    }

    /** The player who can invite, kick and change the banner. Starts as the founder. */
    public UUID leader() {
        return this.leader;
    }

    public boolean isLeader(UUID player) {
        return this.leader.equals(player);
    }

    /** Everyone in the kingdom, leader included, in the order they joined. */
    public Set<UUID> members() {
        return Set.copyOf(this.members);
    }

    public boolean isMember(UUID player) {
        return this.members.contains(player);
    }

    public KingdomRank rank(UUID player) {
        if (this.leader.equals(player)) {
            return KingdomRank.LEADER;
        }
        if (this.officers.contains(player) && this.members.contains(player)) {
            return KingdomRank.OFFICER;
        }
        return this.members.contains(player) ? KingdomRank.MEMBER : KingdomRank.NONE;
    }

    public boolean isBanned(UUID player) {
        return this.banned.contains(player);
    }

    public Set<UUID> banned() {
        return Set.copyOf(this.banned);
    }

    public Settings settings() {
        return this.settings;
    }

    /** The kingdom's map, a grid of maps around its Town Hall. Grows when given empty maps. */
    public KingdomMap map() {
        return this.map;
    }

    /** Every chunk the kingdom has claimed, in the order it claimed them. */
    public Set<ChunkClaim> claims() {
        return Collections.unmodifiableSet(this.claims);
    }

    public boolean hasClaimed(ChunkClaim chunk) {
        return this.claims.contains(chunk);
    }

    /** The kingdom's level, from 1 to {@link KingdomLevel#MAX_LEVEL}. */
    public int level() {
        return this.level;
    }

    /** What the kingdom's level allows: villager and land caps. */
    public KingdomLevel levelInfo() {
        return KingdomLevel.of(this.level);
    }

    /** The villagers who have moved in, by entity UUID. They leave the list when they die. */
    public Set<UUID> residents() {
        return Collections.unmodifiableSet(this.residents);
    }

    /**
     * The tier of every perk the kingdom has unlocked (1 is the first), in the order it unlocked them. Ids of perks
     * that no longer exist are kept so they come back if the perk does. Use {@link KingdomPerks#activeTier} to see
     * what is working.
     */
    public Map<ResourceLocation, Integer> perks() {
        return Collections.unmodifiableMap(this.perks);
    }

    /** The tier the kingdom has unlocked of a perk, or 0. */
    public int perkTier(ResourceLocation perk) {
        return this.perks.getOrDefault(perk, 0);
    }

    /** How many chunks out from the Town Hall's chunk the kingdom's land reaches: its level's radius plus Expansion. */
    public int claimRadius() {
        return this.levelInfo().claimRadius() + KingdomPerks.activeTier(this, KingdomPerks.EXPANSION);
    }

    /** The most chunks the kingdom can hold: a square around the Town Hall's chunk. */
    public int landCap() {
        int side = 2 * this.claimRadius() + 1;
        return side * side;
    }

    /** The Shared Vault's contents. Its menu only shows as many rows as the perk's tier allows. */
    public net.minecraft.world.SimpleContainer vault() {
        return this.vault;
    }

    /** The perk tier the kingdom's Scholars are researching, if any. Its points are already spent. */
    public Optional<KingdomResearch.Research> research() {
        return Optional.ofNullable(this.research);
    }

    /** Kingdom XP earned toward the next level. Starts again from 0 at each level, and stays 0 at the top level. */
    public int xp() {
        return this.xp;
    }

    /** Emeralds waiting in the Town Hall's treasury, from the Tax Collector perk. */
    public int taxes() {
        return this.taxes;
    }

    public boolean isInvited(UUID player) {
        return this.invites.contains(player);
    }

    public Optional<GlobalPos> townHall() {
        return Optional.ofNullable(this.townHall);
    }

    /** The kingdom's banner, a single banner item, if the leader has set one. */
    public Optional<ItemStack> banner() {
        return this.banner.isEmpty() ? Optional.empty() : Optional.of(this.banner.copy());
    }

    /** The kingdom's name, in its colour. */
    public Component displayName() {
        return Component.literal(this.name).withStyle(style -> style.withColor(TextColor.fromRgb(this.color.getTextColor())));
    }

    // ---- Changes, used by KingdomData ----

    Set<UUID> mutableMembers() {
        return this.members;
    }

    Set<UUID> mutableInvites() {
        return this.invites;
    }

    void setLeader(UUID leader) {
        this.leader = leader;
    }

    void setColor(DyeColor color) {
        this.color = color;
    }

    void setBanner(ItemStack banner) {
        this.banner = banner.copyWithCount(1);
    }

    void setTownHall(GlobalPos townHall) {
        this.townHall = townHall;
    }

    Set<UUID> mutableOfficers() {
        return this.officers;
    }

    Set<UUID> mutableBanned() {
        return this.banned;
    }

    void setSettings(Settings settings) {
        this.settings = settings;
    }

    void setMap(KingdomMap map) {
        this.map = map;
    }

    Set<ChunkClaim> mutableClaims() {
        return this.claims;
    }

    void setLevel(int level) {
        this.level = Math.clamp(level, 1, KingdomLevel.MAX_LEVEL);
    }

    Set<UUID> mutableResidents() {
        return this.residents;
    }

    void setPerkTier(ResourceLocation perk, int tier) {
        this.perks.put(perk, tier);
    }

    void setResearch(@org.jetbrains.annotations.Nullable KingdomResearch.Research research) {
        this.research = research;
    }

    void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    void setTaxes(int taxes) {
        this.taxes = Math.max(0, taxes);
    }

    void clearTownHall() {
        this.townHall = null;
    }
}
