package com.luckyowlstudios.kingdoms.test;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.luckyowlstudios.kingdoms.kingdom.ChunkClaim;
import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.kingdom.KingdomData;
import com.luckyowlstudios.kingdoms.kingdom.KingdomCelebration;
import com.luckyowlstudios.kingdoms.kingdom.KingdomLevel;
import com.luckyowlstudios.kingdoms.kingdom.KingdomVillagers;
import com.luckyowlstudios.kingdoms.kingdom.KingdomTavern;
import com.luckyowlstudios.kingdoms.kingdom.KingdomMaps;
import com.luckyowlstudios.kingdoms.kingdom.Homecoming;
import com.luckyowlstudios.kingdoms.kingdom.House;
import com.luckyowlstudios.kingdoms.kingdom.HouseScan;
import com.luckyowlstudios.kingdoms.kingdom.KingdomHouses;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerkEffects;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerk;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerks;
import com.luckyowlstudios.kingdoms.kingdom.KingdomResearch;
import com.luckyowlstudios.kingdoms.kingdom.SharedVault;
import com.luckyowlstudios.kingdoms.kingdom.SharedXp;
import com.luckyowlstudios.kingdoms.kingdom.TaxCollector;
import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import com.luckyowlstudios.kingdoms.kingdom.KingdomScreens;
import com.luckyowlstudios.kingdoms.kingdom.KingdomTeams;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side checks run in CI with {@code ./gradlew :fabric:runGametest}. They only run when the
 * {@code fabric-api.gametest} system property is set, never in normal play.
 */
public class KingdomsGameTests implements FabricGameTest {

    private static final AtomicInteger NEXT_HALL = new AtomicInteger();

    /**
     * Places a Town Hall in a chunk of its own, well away from the test structures (which sit close together and run
     * at the same time), so kingdoms founded by different tests never land on each other's claimed chunk.
     */
    private static BlockPos isolatedHall(GameTestHelper helper) {
        BlockPos pos = isolatedSpot(helper);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.TOWN_HALL.get().defaultBlockState());
        return pos;
    }

    /** A spot in a chunk of its own, well away from every other test, like {@link #isolatedHall} but with nothing placed. */
    private static BlockPos isolatedSpot(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // Far enough apart that a kingdom at the top level never reaches another test's land.
        int chunkX = 4096 + NEXT_HALL.getAndIncrement() * (2 * (KingdomLevel.of(KingdomLevel.MAX_LEVEL).claimRadius() + KingdomPerks.EXPANSION.maxTier()) + 3);
        return new BlockPos(chunkX * 16 + 4, origin.getY() + 2, 4096 * 16 + 4);
    }

    /** Where tests build a house next to a hall from {@link #isolatedHall}: in the hall's chunk, clear of its work blocks. */
    private static BlockPos houseCorner(BlockPos hall) {
        return hall.offset(5, 0, 5);
    }

    /** Researches the next tier of a perk and finishes the research straight away, as if the Scholars were done. */
    private static boolean unlockNow(ServerPlayer player, KingdomPerk perk) {
        if (!KingdomPerks.upgrade(player, perk)) {
            return false;
        }
        Kingdoms.ofPlayer(player).ifPresent(kingdom -> KingdomResearch.finish(player.server, kingdom));
        return true;
    }

    private static boolean unlockNow(ServerPlayer player, String perkId) {
        return KingdomPerks.byId(net.minecraft.resources.ResourceLocation.parse(perkId)).map(perk -> unlockNow(player, perk)).orElse(false);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void scholarsDeskIsAJobSite(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.SCHOLARS_DESK.get());
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getPoiManager().getType(helper.absolutePos(pos))
                .map(type -> type.is(ModVillagers.SCHOLARS_DESK_POI)).orElse(false), "the Scholar's Desk isn't a point of interest"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void villagerAtDeskBecomesScholar(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), ModBlocks.SCHOLARS_DESK.get());
        Villager villager = helper.spawn(EntityType.VILLAGER, 3, 0, 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.isAlive(), "villager died");
            helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.SCHOLAR_PROFESSION.get(),
                    "villager hasn't become a Scholar yet (is " + villager.getVillagerData().getProfession() + ")");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void researchTakesTimeAndScholarsShareTheWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Scholarton " + level.getGameTime(), DyeColor.PURPLE)).orElseThrow();
        Kingdoms.setLevel(level.getServer(), kingdom, 3);
        BlockPos deskA = hall.offset(3, 0, 0);
        BlockPos deskB = hall.offset(-3, 0, 0);
        level.setBlockAndUpdate(deskA, ModBlocks.SCHOLARS_DESK.get().defaultBlockState());
        level.setBlockAndUpdate(deskB, ModBlocks.SCHOLARS_DESK.get().defaultBlockState());
        // Villagers only move into a house with free beds, while it's loaded: keep it loaded like a nearby player would.
        BlockPos marker = TestHouses.build(level, houseCorner(hall), 2, true);
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x() + dx, center.z() + dz, true);
            }
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(level.isPositionEntityTicking(new net.minecraft.world.level.ChunkPos(center.x() + dx, center.z() + dz).getMiddleBlockPosition(hall.getY())),
                            "forced chunks aren't loaded yet");
                }
            }
            // Wait for the light to spread through the house too.
            TestHouses.assertReady(level, marker);
        }).thenExecute(() -> {
            java.util.List<Villager> villagers = new java.util.ArrayList<>();
            try {
                var server = level.getServer();
                int points = KingdomPerks.availablePoints(kingdom);
                helper.assertTrue(KingdomPerks.upgrade(leader, KingdomPerks.WATCHTOWERS), "couldn't start researching Watchtowers");
                helper.assertTrue(kingdom.research().isPresent(), "no research started");
                helper.assertTrue(kingdom.perkTier(KingdomPerks.WATCHTOWERS.id()) == 0, "Watchtowers unlocked straight away");
                helper.assertTrue(KingdomPerks.availablePoints(kingdom) == points - KingdomPerks.WATCHTOWERS.tier(1).cost(),
                        "the research's points weren't spent");
                helper.assertFalse(KingdomPerks.upgrade(leader, KingdomPerks.BOUNTIFUL_HARVEST), "started a second research at once");
                helper.assertTrue(KingdomResearch.workingScholars(server, kingdom) == 0, "Scholars counted before any moved in");

                // Two residents take the Scholar job at the two desks.
                for (BlockPos desk : java.util.List.of(deskA, deskB)) {
                    Villager villager = KingdomVillagers.morning(server, kingdom).orElseThrow(
                            () -> new net.minecraft.gametest.framework.GameTestAssertException("no villager moved in"));
                    villagers.add(villager);
                    villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.SCHOLAR_PROFESSION.get()));
                    villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), desk));
                }
                helper.assertTrue(KingdomResearch.workingScholars(server, kingdom) == 2, "two Scholars should be working, counted "
                        + KingdomResearch.workingScholars(server, kingdom));

                // A Scholar whose desk is gone doesn't count.
                level.removeBlock(deskB, false);
                helper.assertTrue(KingdomResearch.workingScholars(server, kingdom) == 1, "a Scholar without a desk still counted");
                level.setBlockAndUpdate(deskB, ModBlocks.SCHOLARS_DESK.get().defaultBlockState());

                // The research is saved with the kingdom, progress and all.
                int duration = KingdomPerks.WATCHTOWERS.tier(1).researchSeconds();
                helper.assertFalse(KingdomResearch.work(server, kingdom, duration - 1), "research finished early");
                var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                Kingdom loaded = Kingdom.CODEC.parse(ops, Kingdom.CODEC.encodeStart(ops, kingdom).getOrThrow()).getOrThrow();
                helper.assertTrue(loaded.research().map(r -> r.progress() == duration - 1).orElse(false), "research progress wasn't saved");

                helper.assertTrue(KingdomResearch.work(server, kingdom, 1), "research didn't finish");
                helper.assertTrue(kingdom.perkTier(KingdomPerks.WATCHTOWERS.id()) == 1 && kingdom.research().isEmpty(),
                        "Watchtowers wasn't unlocked when the research finished");

                // Cancelling gives the points back.
                int before = KingdomPerks.availablePoints(kingdom);
                helper.assertTrue(KingdomPerks.upgrade(leader, KingdomPerks.BOUNTIFUL_HARVEST), "couldn't start the next research");
                helper.assertTrue(KingdomResearch.cancel(leader), "couldn't cancel");
                helper.assertTrue(kingdom.research().isEmpty() && KingdomPerks.availablePoints(kingdom) == before,
                        "cancelling didn't give the points back");
            } finally {
                Kingdoms.leave(leader);
                villagers.forEach(net.minecraft.world.entity.Entity::discard);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setChunkForced(center.x() + dx, center.z() + dz, false);
                    }
                }
            }
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void townHallIsAJobSite(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.TOWN_HALL.get());
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getPoiManager().getType(helper.absolutePos(pos))
                .map(type -> type.is(ModVillagers.TOWN_HALL_POI)).orElse(false), "town hall isn't a point of interest"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void villagerAtTownHallBecomesAdvisor(GameTestHelper helper) {
        // On the floor, so the villager can walk up to it (y 2 would leave it floating out of reach).
        helper.setBlock(new BlockPos(1, 0, 1), ModBlocks.TOWN_HALL.get());
        Villager villager = helper.spawn(EntityType.VILLAGER, 3, 0, 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.isAlive(), "villager died");
            helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.ADVISOR_PROFESSION.get(),
                    "villager hasn't become the Advisor yet (is " + villager.getVillagerData().getProfession()
                            + ", job site memory " + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE) + ")");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void foundingAKingdomAtATownHall(GameTestHelper helper) {
        BlockPos hall = isolatedHall(helper);
        String name = "Testoria " + helper.getLevel().getGameTime();

        ServerPlayer founder = helper.makeMockServerPlayerInLevel();
        founder.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        helper.assertTrue(Kingdoms.cleanName("   ").isEmpty(), "blank names should be refused");
        helper.assertTrue(Kingdoms.tryCreate(founder, new CreateKingdomPayload(hall, "   ", DyeColor.RED)).isEmpty(), "founded with a blank name");

        Optional<Kingdom> kingdom = Kingdoms.tryCreate(founder, new CreateKingdomPayload(hall, "  " + name + "  ", DyeColor.RED));
        helper.assertTrue(kingdom.isPresent(), "couldn't found a kingdom");
        helper.assertTrue(kingdom.get().name().equals(name), "name wasn't trimmed: '" + kingdom.get().name() + "'");
        helper.assertTrue(kingdom.get().color() == DyeColor.RED, "wrong colour");
        helper.assertTrue(Kingdoms.ofPlayer(founder).isPresent(), "founder isn't in the kingdom");
        helper.assertTrue(Kingdoms.atTownHall(founder.server, GlobalPos.of(helper.getLevel().dimension(), hall)).isPresent(),
                "town hall isn't set up");

        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        other.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        helper.assertTrue(Kingdoms.tryCreate(other, new CreateKingdomPayload(hall, name + " II", DyeColor.BLUE)).isEmpty(),
                "a second kingdom was founded at a set-up town hall");

        // Another Town Hall in the first kingdom's chunk: that land is taken.
        BlockPos sameChunk = hall.offset(2, 0, 2);
        helper.getLevel().setBlockAndUpdate(sameChunk, ModBlocks.TOWN_HALL.get().defaultBlockState());
        other.moveTo(sameChunk.getX() + 1.5, sameChunk.getY(), sameChunk.getZ() + 0.5);
        helper.assertTrue(Kingdoms.tryCreate(other, new CreateKingdomPayload(sameChunk, name + " II", DyeColor.BLUE)).isEmpty(),
                "a kingdom was founded on another kingdom's land");

        BlockPos hall2 = isolatedHall(helper);
        other.moveTo(hall2.getX() + 1.5, hall2.getY(), hall2.getZ() + 0.5);
        founder.moveTo(hall2.getX() + 1.5, hall2.getY(), hall2.getZ() + 0.5);
        helper.assertTrue(Kingdoms.tryCreate(other, new CreateKingdomPayload(hall2, name.toUpperCase(), DyeColor.BLUE)).isEmpty(),
                "two kingdoms got the same name");
        helper.assertTrue(Kingdoms.tryCreate(founder, new CreateKingdomPayload(hall2, name + " II", DyeColor.BLUE)).isEmpty(),
                "a player founded a second kingdom");
        helper.assertTrue(Kingdoms.tryCreate(other, new CreateKingdomPayload(hall2, name + " II", DyeColor.BLUE)).isPresent(),
                "second player couldn't found their own kingdom at a new town hall");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void townHallShowsItsKingdom(GameTestHelper helper) {
        BlockPos hall = isolatedHall(helper);
        TownHallBlockEntity entity = (TownHallBlockEntity) helper.getLevel().getBlockEntity(hall);
        helper.assertTrue(entity != null, "town hall has no block entity");
        entity.refresh();
        helper.assertFalse(entity.hasKingdom(), "a free town hall shows a kingdom");

        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Bookham " + helper.getLevel().getGameTime(), DyeColor.CYAN)).orElseThrow();
        helper.assertTrue(entity.kingdomColor() == DyeColor.CYAN, "town hall didn't pick up its new kingdom's colour");
        Kingdoms.setColor(leader, DyeColor.LIME);
        helper.assertTrue(entity.kingdomColor() == DyeColor.LIME, "town hall didn't follow the kingdom's new colour");

        helper.assertFalse(entity.isBookOpen(), "book is open with nobody reading");
        TownHallBlockEntity.onScreenOpened(leader, hall);
        helper.assertTrue(entity.isBookOpen(), "book didn't open with the Town Hall screen");
        TownHallBlockEntity.onScreenClosed(leader, hall);
        helper.assertFalse(entity.isBookOpen(), "book didn't close with the Town Hall screen");

        TownHallBlockEntity.onScreenOpened(leader, hall);
        Kingdoms.leave(leader);
        helper.assertFalse(entity.hasKingdom(), "town hall still shows a disbanded kingdom");
        helper.assertFalse(entity.isBookOpen(), "book stayed open after the kingdom disbanded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void kingdomsWorkLikeTeams(GameTestHelper helper) {
        BlockPos hall = isolatedHall(helper);
        String name = "Teamland " + helper.getLevel().getGameTime();

        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, name, DyeColor.GREEN)).orElseThrow();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();

        helper.assertFalse(Kingdoms.join(friend, name), "joined without an invite");
        helper.assertFalse(Kingdoms.invite(friend, stranger), "a non-member invited someone");
        helper.assertTrue(Kingdoms.invite(leader, friend), "leader couldn't invite");
        helper.assertTrue(Kingdoms.join(friend, name.toLowerCase()), "invited player couldn't join");
        helper.assertTrue(kingdom.isMember(friend.getUUID()), "friend isn't a member");
        helper.assertFalse(kingdom.isInvited(friend.getUUID()), "invite wasn't used up");
        helper.assertTrue(leader.server.getScoreboard().getPlayerTeam(KingdomTeams.teamName(kingdom)) != null, "no vanilla team");

        helper.assertFalse(Kingdoms.kick(friend, leader.getUUID()), "a member kicked the leader");
        helper.assertFalse(Kingdoms.kick(leader, leader.getUUID()), "leader kicked themselves");
        helper.assertTrue(Kingdoms.kick(leader, friend.getUUID()), "leader couldn't kick");
        helper.assertFalse(kingdom.isMember(friend.getUUID()), "kicked player is still a member");

        helper.assertFalse(Kingdoms.setBanner(leader, new ItemStack(net.minecraft.world.item.Items.STICK)), "a stick became the banner");
        helper.assertTrue(Kingdoms.setBanner(leader, new ItemStack(net.minecraft.world.item.Items.GREEN_BANNER, 3)), "couldn't set banner");
        helper.assertTrue(kingdom.banner().map(b -> b.is(net.minecraft.world.item.Items.GREEN_BANNER) && b.getCount() == 1).orElse(false), "banner not saved");

        Kingdoms.invite(leader, friend);
        Kingdoms.join(friend, name);
        helper.assertTrue(Kingdoms.leave(leader), "leader couldn't leave");
        helper.assertTrue(kingdom.isLeader(friend.getUUID()), "leadership didn't pass to the remaining member");
        helper.assertTrue(Kingdoms.leave(friend), "last member couldn't leave");
        helper.assertTrue(KingdomData.get(leader.server).byName(name).isEmpty(), "empty kingdom wasn't disbanded");
        helper.assertTrue(Kingdoms.atTownHall(leader.server, GlobalPos.of(helper.getLevel().dimension(), hall)).isEmpty(),
                "disbanded kingdom still holds the town hall");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void kingdomRanksBansAndSettings(GameTestHelper helper) {
        BlockPos hall = isolatedHall(helper);
        String name = "Rankshire " + helper.getLevel().getGameTime();
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, name, DyeColor.PURPLE)).orElseThrow();
        ServerPlayer officer = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        ServerPlayer outsider = helper.makeMockServerPlayerInLevel();
        Kingdoms.invite(leader, officer);
        Kingdoms.join(officer, name);
        Kingdoms.invite(leader, member);
        Kingdoms.join(member, name);

        helper.assertFalse(Kingdoms.promote(officer, member.getUUID()), "a member promoted someone");
        helper.assertTrue(Kingdoms.promote(leader, officer.getUUID()), "leader couldn't promote");
        helper.assertTrue(kingdom.rank(officer.getUUID()) == KingdomRank.OFFICER, "not an officer");
        helper.assertTrue(Kingdoms.invite(officer, outsider), "officer couldn't invite");
        helper.assertFalse(Kingdoms.kick(officer, leader.getUUID()), "officer kicked the leader");
        helper.assertTrue(Kingdoms.ban(officer, member.getUUID()), "officer couldn't ban a member");
        helper.assertFalse(kingdom.isMember(member.getUUID()), "banned member is still in");
        helper.assertFalse(Kingdoms.join(member, name), "banned player rejoined");
        helper.assertTrue(Kingdoms.unban(officer, member.getUUID()), "couldn't unban");

        // Open kingdom: anyone not banned can join without an invite.
        helper.assertFalse(Kingdoms.setSettings(officer, new Kingdom.Settings(false, false)), "officer changed settings");
        helper.assertTrue(Kingdoms.setSettings(leader, new Kingdom.Settings(false, true)), "leader couldn't change settings");
        helper.assertTrue(Kingdoms.join(member, name), "couldn't join an open kingdom");
        helper.assertTrue(leader.server.getScoreboard().getPlayerTeam(KingdomTeams.teamName(kingdom)).isAllowFriendlyFire(),
                "friendly fire setting didn't reach the team");

        helper.assertTrue(Kingdoms.setColor(leader, DyeColor.CYAN) && kingdom.color() == DyeColor.CYAN, "couldn't recolor");
        helper.assertTrue(Kingdoms.demote(leader, officer.getUUID()), "couldn't demote");
        helper.assertTrue(Kingdoms.promote(leader, officer.getUUID()) && Kingdoms.promote(leader, officer.getUUID()), "couldn't promote twice");
        helper.assertTrue(kingdom.isLeader(officer.getUUID()) && kingdom.rank(leader.getUUID()) == KingdomRank.OFFICER,
                "promoting an officer should hand over leadership");

        var view = KingdomScreens.view(officer, kingdom);
        helper.assertTrue(view.viewerRank() == KingdomRank.LEADER && view.members().size() == 3, "screen should list the 3 members, not " + view.members().size());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void townHallClaimsItsChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ChunkClaim chunk = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        KingdomData data = KingdomData.get(level.getServer());
        helper.assertTrue(data.claimedBy(chunk).isEmpty(), "chunk was claimed before founding");

        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Claimholm " + level.getGameTime(), DyeColor.ORANGE)).orElseThrow();
        helper.assertTrue(kingdom.claims().equals(java.util.Set.of(chunk)), "founding should claim the hall's chunk, got " + kingdom.claims());
        helper.assertTrue(data.claimedBy(chunk).map(owner -> owner == kingdom).orElse(false), "chunk isn't looked up as the kingdom's");
        var view = KingdomScreens.view(leader, kingdom);
        helper.assertTrue(view.claims().equals(java.util.List.of(chunk.chunkPos())) && view.claimCount() == 1,
                "screen should get the claimed chunk, got " + view.claims());

        // Breaking the Town Hall gives up its chunk.
        level.removeBlock(hall, false);
        helper.assertTrue(kingdom.townHall().isEmpty(), "town hall wasn't cleared");
        helper.assertTrue(kingdom.claims().isEmpty() && data.claimedBy(chunk).isEmpty(), "breaking the town hall didn't free its chunk");

        // A new Town Hall becomes the seat and claims its own chunk.
        BlockPos newHall = isolatedHall(helper);
        leader.moveTo(newHall.getX() + 1.5, newHall.getY(), newHall.getZ() + 0.5);
        Kingdoms.onTownHallUsed(leader, newHall);
        ChunkClaim newChunk = ChunkClaim.of(GlobalPos.of(level.dimension(), newHall));
        helper.assertTrue(kingdom.townHall().isPresent() && kingdom.hasClaimed(newChunk), "new town hall didn't claim its chunk");

        // Disbanding frees all of it.
        Kingdoms.leave(leader);
        helper.assertTrue(data.claimedBy(newChunk).isEmpty(), "disbanded kingdom still holds land");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void kingdomMapGrowsWithEmptyMaps(GameTestHelper helper) {
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        leader.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // creative players keep their maps
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Mapton " + helper.getLevel().getGameTime(), DyeColor.YELLOW)).orElseThrow();
        helper.assertTrue(KingdomMaps.radius(leader, kingdom) == 0, "map should start as a single map");
        ItemStack maps = new ItemStack(net.minecraft.world.item.Items.MAP, 5);
        for (int i = 0; i < 5; i++) {
            KingdomMaps.grow(leader, kingdom, maps);
        }
        helper.assertTrue(KingdomMaps.radius(leader, kingdom) == KingdomMaps.MAX_RADIUS, "map didn't grow to the max");
        var map = KingdomMaps.getOrCreate(leader, kingdom).orElseThrow();
        int width = KingdomMaps.MAX_RADIUS * 2 + 1;
        helper.assertTrue(map.tiles().size() == width * width, "should be a " + width + "x" + width + " grid, got " + map.tiles().size() + " maps");
        for (var tile : map.tiles()) {
            var data = helper.getLevel().getMapData(tile.id());
            helper.assertTrue(data != null && data.scale == 0, "growing the map shouldn't zoom it out");
            int x = hall.getX() + tile.x() * 128;
            int z = hall.getZ() + tile.z() * 128;
            helper.assertTrue(data.centerX == x && data.centerZ == z,
                    "map " + tile.x() + ", " + tile.z() + " should be centred on " + x + ", " + z + ", not " + data.centerX + ", " + data.centerZ);
        }
        helper.assertTrue(maps.getCount() == 1, "should use one empty map per step, " + maps.getCount() + " left");
        KingdomMaps.explore(leader);
        KingdomScreens.open(leader, kingdom);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void levellingUpGrowsTheLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
                Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Levelburg " + level.getGameTime(), DyeColor.LIME)).orElseThrow();
        helper.assertTrue(kingdom.level() == 1 && kingdom.claims().size() == KingdomLevel.of(1).landCap(),
                "new kingdom should be level 1 with " + KingdomLevel.of(1).landCap() + " chunks, got level " + kingdom.level() + ", " + kingdom.claims().size());

        // A member gaining experience gives the kingdom the same XP; not quite enough isn't a level.
        int needed = KingdomLevel.of(2).xp();
        leader.giveExperiencePoints(needed - 1);
        helper.assertTrue(kingdom.level() == 1 && kingdom.xp() == needed - 1, "kingdom should have " + (needed - 1) + " XP at level 1, got "
                + kingdom.xp() + " at level " + kingdom.level());
        helper.assertTrue(!KingdomCelebration.isCelebrating(kingdom), "celebrating before levelling up");
        leader.giveExperiencePoints(6);
        helper.assertTrue(kingdom.level() == 2, "should be level 2 after " + (needed + 5) + " XP");
        helper.assertTrue(kingdom.xp() == 5, "leftover XP should carry over, got " + kingdom.xp());
        helper.assertTrue(KingdomCelebration.isCelebrating(kingdom), "levelling up didn't start a celebration");
        helper.assertTrue(KingdomLevel.of(3).xp() > KingdomLevel.of(2).xp(), "each level should take more XP than the last");
        // Losing experience doesn't take kingdom XP away.
        leader.giveExperiencePoints(-3);
        helper.assertTrue(kingdom.xp() == 5, "lost experience changed kingdom XP");
        helper.assertTrue(kingdom.claims().size() == KingdomLevel.of(2).landCap(), "land didn't grow to " + KingdomLevel.of(2).landCap()
                + " chunks, got " + kingdom.claims().size());
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        ChunkClaim corner = new ChunkClaim(level.dimension(), center.x() + 1, center.z() + 1);
        helper.assertTrue(KingdomData.get(level.getServer()).claimedBy(corner).map(owner -> owner == kingdom).orElse(false),
                "the ring around the town hall wasn't claimed");
        var view = KingdomScreens.view(leader, kingdom);
        helper.assertTrue(view.growth().level() == 2 && view.growth().landCap() == KingdomLevel.of(2).landCap()
                && view.growth().homes() == 0
                && view.growth().xp() == 5 && view.growth().xpToNext() == KingdomLevel.of(3).xp(), "screen got the wrong growth: " + view.growth());

        // Lowering the level (operator command) shrinks the land back.
        Kingdoms.setLevel(level.getServer(), kingdom, 1);
        helper.assertTrue(kingdom.claims().equals(java.util.Set.of(center)), "land didn't shrink back to the hall's chunk");
        helper.assertTrue(KingdomData.get(level.getServer()).claimedBy(corner).isEmpty(), "released chunk is still claimed");
        Kingdoms.leave(leader);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void perksUnlockAndUpgradeWithPoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        String name = "Perkford " + level.getGameTime();
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, name, DyeColor.BLUE)).orElseThrow();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        Kingdoms.invite(leader, member);
        Kingdoms.join(member, name);

        helper.assertTrue(KingdomPerks.availablePoints(kingdom) == KingdomPerks.POINTS_PER_LEVEL, "a new kingdom should have one level's points");
        helper.assertFalse(unlockNow(leader, KingdomPerks.SHARED_XP), "unlocked Shared XP below its level");
        Kingdoms.setLevel(level.getServer(), kingdom, 3);
        helper.assertFalse(unlockNow(member, KingdomPerks.SHARED_XP), "a plain member spent perk points");
        helper.assertTrue(unlockNow(leader, KingdomPerks.SHARED_XP.id().toString()), "leader couldn't unlock Shared XP");
        helper.assertTrue(unlockNow(leader, KingdomPerks.SHARED_XP), "leader couldn't upgrade Shared XP to tier 2");
        helper.assertTrue(kingdom.perkTier(KingdomPerks.SHARED_XP.id()) == 2, "Shared XP should be tier 2");
        helper.assertFalse(unlockNow(leader, KingdomPerks.SHARED_XP), "tier 3 bought below its level");
        int points = KingdomPerks.availablePoints(kingdom);
        helper.assertTrue(points == 3 * KingdomPerks.POINTS_PER_LEVEL - 2, "wrong points left: " + points);

        // Lowering the level switches off the tiers above it, but keeps them.
        Kingdoms.setLevel(level.getServer(), kingdom, 2);
        helper.assertTrue(KingdomPerks.activeTier(kingdom, KingdomPerks.SHARED_XP) == 1, "tier 2 should stop working at level 2");
        Kingdoms.setLevel(level.getServer(), kingdom, 3);
        helper.assertTrue(KingdomPerks.activeTier(kingdom, KingdomPerks.SHARED_XP) == 2, "tier 2 should work again at level 3");

        var view = KingdomScreens.view(leader, kingdom);
        helper.assertTrue(view.growth().perkTier(KingdomPerks.SHARED_XP.id()) == 2 && view.growth().perkPoints() == points,
                "screen got the wrong perks: " + view.growth());

        // Saved and loaded with the kingdom.
        var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        Kingdom loaded = Kingdom.CODEC.parse(ops, Kingdom.CODEC.encodeStart(ops, kingdom).getOrThrow()).getOrThrow();
        helper.assertTrue(loaded.perkTier(KingdomPerks.SHARED_XP.id()) == 2, "perk tier wasn't saved");

        Kingdoms.leave(member);
        Kingdoms.leave(leader);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sharedXpGivesOtherMembersAShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        String name = "Sharewick " + level.getGameTime();
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, name, DyeColor.GREEN)).orElseThrow();
        ServerPlayer a = helper.makeMockServerPlayerInLevel();
        ServerPlayer b = helper.makeMockServerPlayerInLevel();
        for (ServerPlayer player : java.util.List.of(a, b)) {
            Kingdoms.invite(leader, player);
            Kingdoms.join(player, name);
        }

        // Without the perk, nothing is shared.
        leader.giveExperiencePoints(80);
        helper.assertTrue(a.totalExperience == 0 && b.totalExperience == 0, "XP was shared without the perk");

        Kingdoms.setLevel(level.getServer(), kingdom, 2);
        helper.assertTrue(unlockNow(leader, KingdomPerks.SHARED_XP), "couldn't unlock Shared XP");
        // Tier 1, three members: each other member gets 1/8.
        helper.assertTrue(Math.abs(SharedXp.share(80, 3, 1) - 10) < 1e-9, "wrong share: " + SharedXp.share(80, 3, 1));
        helper.assertTrue(Math.abs(SharedXp.share(70, 2, 1) - 10) < 1e-9, "two members should share 1/7");
        leader.giveExperiencePoints(80);
        helper.assertTrue(a.totalExperience == 10 && b.totalExperience == 10,
                "each member should get 10 XP, got " + a.totalExperience + " and " + b.totalExperience);
        helper.assertTrue(leader.totalExperience == 160, "shared XP came back to the leader: " + leader.totalExperience);

        // Fractions add up: 4 XP gives each other member half a point, so two gains make one point.
        leader.giveExperiencePoints(4);
        helper.assertTrue(a.totalExperience == 10, "half a point was paid out early");
        leader.giveExperiencePoints(4);
        helper.assertTrue(a.totalExperience == 11 && b.totalExperience == 11, "two half points didn't make one, got " + a.totalExperience);

        // Losing XP (enchanting, say) isn't shared.
        leader.giveExperiencePoints(-20);
        helper.assertTrue(a.totalExperience == 11, "lost XP was shared");

        for (ServerPlayer player : java.util.List.of(a, b, leader)) {
            Kingdoms.leave(player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void taxCollectorFillsTheTreasury(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        leader.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Taxbury " + level.getGameTime(), DyeColor.ORANGE)).orElseThrow();
        Kingdoms.setLevel(level.getServer(), kingdom, 3);
        // Villagers only move into a house with free beds, while it's loaded: keep it loaded like a nearby player would.
        BlockPos marker = TestHouses.build(level, houseCorner(hall), 2, true);
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x() + dx, center.z() + dz, true);
            }
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(level.isPositionEntityTicking(new net.minecraft.world.level.ChunkPos(center.x() + dx, center.z() + dz).getMiddleBlockPosition(hall.getY())),
                            "forced chunks aren't loaded yet");
                }
            }
            // Wait for the light to spread through the house too.
            TestHouses.assertReady(level, marker);
        }).thenExecute(() -> {
            Villager jobless = KingdomVillagers.morning(level.getServer(), kingdom).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("no villager moved in"));
            Villager farmer = KingdomVillagers.morning(level.getServer(), kingdom).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("no second villager moved in"));
            try {
                jobless.setVillagerData(jobless.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
                farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(3));

                TaxCollector.morning(level.getServer(), kingdom);
                helper.assertTrue(kingdom.taxes() == 0, "taxes without the perk");

                helper.assertFalse(unlockNow(leader, KingdomPerks.TAX_COLLECTOR), "unlocked Tax Collector without Trade Deals");
                helper.assertTrue(unlockNow(leader, KingdomPerks.BOUNTIFUL_HARVEST)
                        && unlockNow(leader, KingdomPerks.TRADE_DEALS), "couldn't unlock the perks before Tax Collector");
                helper.assertTrue(unlockNow(leader, KingdomPerks.TAX_COLLECTOR), "couldn't unlock Tax Collector");
                TaxCollector.morning(level.getServer(), kingdom);
                helper.assertTrue(kingdom.taxes() == 2, "tier 1 should take 1 per villager, got " + kingdom.taxes());
                helper.assertTrue(unlockNow(leader, KingdomPerks.TAX_COLLECTOR), "couldn't upgrade Tax Collector");
                TaxCollector.morning(level.getServer(), kingdom);
                helper.assertTrue(kingdom.taxes() == 2 + 3, "tier 2: the farmer should pay 2, got " + (kingdom.taxes() - 2));
                Kingdoms.setLevel(level.getServer(), kingdom, 4);
                helper.assertTrue(unlockNow(leader, KingdomPerks.TAX_COLLECTOR), "couldn't buy Tax Collector tier 3");
                TaxCollector.morning(level.getServer(), kingdom);
                helper.assertTrue(kingdom.taxes() == 5 + 4, "tier 3: the journeyman farmer should pay 3, got " + (kingdom.taxes() - 5));

                // The treasury fills up.
                int cap = TaxCollector.treasuryCap(level.getServer(), kingdom);
                for (int day = 0; day < 100; day++) {
                    TaxCollector.morning(level.getServer(), kingdom);
                }
                helper.assertTrue(kingdom.taxes() == cap, "treasury should stop at " + cap + ", has " + kingdom.taxes());
                helper.assertTrue(KingdomScreens.view(leader, kingdom).growth().taxes() == cap, "screen got the wrong treasury");

                helper.assertTrue(TaxCollector.collect(leader), "leader couldn't collect");
                helper.assertTrue(kingdom.taxes() == 0, "treasury wasn't emptied");
                int held = leader.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
                helper.assertTrue(held == cap, "leader should hold " + cap + " emeralds, has " + held);
                helper.assertFalse(TaxCollector.collect(leader), "collected from an empty treasury");
            } finally {
                Kingdoms.leave(leader);
                java.util.List.of(jobless, farmer).forEach(net.minecraft.world.entity.Entity::discard);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setChunkForced(center.x() + dx, center.z() + dz, false);
                    }
                }
            }
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void landPerksProtectAndGrowTheKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        String name = "Wallton " + level.getGameTime();
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, name, DyeColor.GRAY)).orElseThrow();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        Kingdoms.invite(leader, member);
        Kingdoms.join(member, name);
        ServerPlayer outsider = helper.makeMockServerPlayerInLevel();
        Kingdoms.setLevel(level.getServer(), kingdom, KingdomLevel.MAX_LEVEL);
        BlockPos inside = hall.offset(3, 0, 3);

        // Fortified Walls: outsiders are stopped, members aren't; explosions only from tier 2.
        helper.assertFalse(KingdomPerkEffects.wallsStop(outsider, inside, KingdomPerkEffects.WALLS_BREAKING), "walls up before the perk");
        helper.assertTrue(unlockNow(leader, KingdomPerks.WATCHTOWERS) && unlockNow(leader, KingdomPerks.FORTIFIED_WALLS),
                "couldn't unlock Fortified Walls");
        helper.assertTrue(KingdomPerkEffects.wallsStop(outsider, inside, KingdomPerkEffects.WALLS_BREAKING), "an outsider can break blocks");
        helper.assertFalse(KingdomPerkEffects.wallsStop(member, inside, KingdomPerkEffects.WALLS_BREAKING), "a member can't break blocks");
        helper.assertFalse(KingdomPerkEffects.wallsStop(outsider, inside, KingdomPerkEffects.WALLS_USING), "tier 3 protection at tier 1");
        helper.assertFalse(KingdomPerkEffects.wallsStopExplosion(level, inside), "explosions stopped at tier 1");
        helper.assertTrue(unlockNow(leader, KingdomPerks.FORTIFIED_WALLS), "couldn't upgrade Fortified Walls");
        helper.assertTrue(KingdomPerkEffects.wallsStopExplosion(level, inside), "explosions not stopped at tier 2");
        helper.assertFalse(KingdomPerkEffects.wallsStopExplosion(level, hall.offset(16 * 20, 0, 0)), "explosions stopped outside the land");

        // Bountiful Harvest only works on the kingdom's land.
        helper.assertTrue(KingdomPerkEffects.bountifulHarvestChance(level, inside) == 0, "harvest bonus before the perk");
        helper.assertTrue(unlockNow(leader, KingdomPerks.BOUNTIFUL_HARVEST), "couldn't unlock Bountiful Harvest");
        helper.assertTrue(KingdomPerkEffects.bountifulHarvestChance(level, inside) > 0, "no harvest bonus on the land");

        // Trade Deals: at least one off for members, nothing for outsiders.
        helper.assertTrue(unlockNow(leader, KingdomPerks.TRADE_DEALS), "couldn't unlock Trade Deals");
        helper.assertTrue(KingdomPerkEffects.tradeDiscount(level, inside, member, 5) == 1, "members should get 1 off a 5 emerald trade");
        helper.assertTrue(KingdomPerkEffects.tradeDiscount(level, inside, member, 30) == 3, "members should get 10% off");
        helper.assertTrue(KingdomPerkEffects.tradeDiscount(level, inside, outsider, 30) == 0, "an outsider got a discount");

        // Expansion grows the land by a ring.
        int before = kingdom.claims().size();
        helper.assertTrue(unlockNow(leader, KingdomPerks.WELCOMING) && unlockNow(leader, KingdomPerks.EXPANSION),
                "couldn't unlock Expansion");
        helper.assertTrue(kingdom.claimRadius() == KingdomLevel.of(KingdomLevel.MAX_LEVEL).claimRadius() + 1, "Expansion didn't add a ring");
        helper.assertTrue(kingdom.claims().size() == kingdom.landCap() && kingdom.claims().size() > before,
                "land didn't grow: " + before + " to " + kingdom.claims().size());

        // Homecoming takes a member back to the Town Hall, then has a cooldown.
        helper.assertTrue(unlockNow(leader, KingdomPerks.SHARED_XP) && unlockNow(leader, KingdomPerks.WELL_FED)
                && unlockNow(leader, KingdomPerks.HOMECOMING), "couldn't unlock Homecoming");
        member.teleportTo(hall.getX() + 200.5, hall.getY() + 10, hall.getZ() + 0.5);
        helper.assertTrue(Homecoming.goHome(member), "couldn't go home");
        helper.assertTrue(member.distanceToSqr(hall.getCenter()) < 4 * 4, "member didn't arrive at the town hall");
        helper.assertFalse(Homecoming.goHome(member), "went home twice without a cooldown");

        // Shared Vault: at the Town Hall only, and its contents are saved with the kingdom.
        helper.assertTrue(unlockNow(leader, KingdomPerks.SHARED_VAULT), "couldn't unlock Shared Vault: "
                + KingdomPerks.availablePoints(kingdom) + " points left");
        helper.assertTrue(SharedVault.open(member), "couldn't open the vault at the town hall");
        member.closeContainer();
        outsider.teleportTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 2.5);
        helper.assertFalse(SharedVault.open(outsider), "an outsider opened the vault");
        kingdom.vault().setItem(4, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        Kingdom loaded = Kingdom.CODEC.parse(ops, Kingdom.CODEC.encodeStart(ops, kingdom).getOrThrow()).getOrThrow();
        helper.assertTrue(loaded.vault().getItem(4).is(net.minecraft.world.item.Items.DIAMOND) && loaded.vault().getItem(4).getCount() == 3,
                "vault contents weren't saved");

        Kingdoms.leave(member);
        Kingdoms.leave(leader);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void scholarFromOutsideJoinsTheKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Deskford " + level.getGameTime(), DyeColor.CYAN)).orElseThrow();
        BlockPos desk = hall.east(3);
        level.setBlockAndUpdate(desk, ModBlocks.SCHOLARS_DESK.get().defaultBlockState());
        // Keep the hall's chunk loaded and ticking, like a nearby player would, so the villager can be found.
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        level.setChunkForced(center.x(), center.z(), true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(hall), "the hall's chunk isn't loaded yet"))
                .thenExecute(() -> {
                    try {
                        // A villager that didn't move in through the Town Hall (a spawn egg, a nearby village) takes the desk.
                        Villager villager = EntityType.VILLAGER.create(level);
                        villager.moveTo(desk.getX() + 0.5, desk.getY() + 1, desk.getZ() + 1.5);
                        level.addFreshEntity(villager);
                        villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), desk));
                        helper.assertFalse(KingdomVillagers.checkWorker(villager), "a villager without the Scholar job joined");

                        villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.SCHOLAR_PROFESSION.get()));
                        helper.assertTrue(KingdomVillagers.checkWorker(villager), "the Scholar didn't join the kingdom");
                        helper.assertTrue(kingdom.residents().contains(villager.getUUID()), "the Scholar isn't a resident");
                        helper.assertFalse(KingdomVillagers.checkWorker(villager), "the Scholar joined twice");
                        helper.assertTrue(KingdomResearch.workingScholars(level.getServer(), kingdom) == 1, "the Scholar isn't working");
                        villager.discard();
                    } finally {
                        level.setChunkForced(center.x(), center.z(), false);
                        Kingdoms.leave(leader);
                    }
                }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void creativePlayersUnlockPerksStraightAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Creativia " + level.getGameTime(), DyeColor.CYAN)).orElseThrow();
        String rally = KingdomPerks.RALLY.id().toString();

        // Unlocking the perk being researched finishes the research.
        leader.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(KingdomPerks.upgrade(leader, KingdomPerks.BOUNTIFUL_HARVEST), "couldn't start researching");
        helper.assertFalse(KingdomPerks.forceUnlock(leader, rally), "unlocked a perk outside Creative");
        leader.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        helper.assertTrue(KingdomPerks.forceUnlock(leader, KingdomPerks.BOUNTIFUL_HARVEST.id().toString()), "Creative couldn't finish the research");
        helper.assertTrue(kingdom.research().isEmpty() && kingdom.perkTier(KingdomPerks.BOUNTIFUL_HARVEST.id()) == 1,
                "the research didn't finish");

        // Rally needs level 3, Fortified Walls and more points than the kingdom has: Creative skips all that.
        helper.assertTrue(KingdomPerks.forceUnlock(leader, rally), "Creative couldn't unlock Rally");
        helper.assertTrue(kingdom.perkTier(KingdomPerks.RALLY.id()) == 1, "Rally isn't at tier 1");
        helper.assertTrue(KingdomPerks.forceUnlock(leader, rally) && KingdomPerks.forceUnlock(leader, rally), "Creative couldn't upgrade Rally");
        helper.assertFalse(KingdomPerks.forceUnlock(leader, rally), "upgraded Rally past its last tier");
        Kingdoms.leave(leader);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void townHallAdvisorJoinsTheKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Advisorton " + level.getGameTime(), DyeColor.CYAN)).orElseThrow();

        Villager villager = EntityType.VILLAGER.create(level);
        villager.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        level.addFreshEntity(villager);
        // An unemployed villager doesn't join, even with the hall as its job site.
        villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), hall));
        helper.assertFalse(KingdomVillagers.checkAdvisor(villager), "a villager without the Advisor job joined");

        // Taking the Town Hall job makes it a resident, counted toward the cap.
        villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.ADVISOR_PROFESSION.get()));
        helper.assertTrue(KingdomVillagers.checkAdvisor(villager), "the Advisor didn't join the kingdom");
        helper.assertTrue(kingdom.residents().contains(villager.getUUID()) && villager.getTags().contains(KingdomVillagers.RESIDENT_TAG),
                "the Advisor isn't a resident");
        helper.assertFalse(KingdomVillagers.checkAdvisor(villager), "the Advisor joined twice");
        var view = KingdomScreens.view(leader, kingdom);
        helper.assertTrue(view.growth().villagers() == 1, "the Advisor doesn't count toward the villagers, got " + view.growth().villagers());

        villager.discard();
        helper.assertTrue(kingdom.residents().isEmpty(), "a removed Advisor still counts");
        Kingdoms.leave(leader);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tavernIsAJobSite(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.TAVERN.get());
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getPoiManager().getType(helper.absolutePos(pos))
                .map(type -> type.is(ModVillagers.TAVERN_POI)).orElse(false), "tavern isn't a point of interest"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void innkeeperBringsAVillagerAtNight(GameTestHelper helper) {
        // Night numbers go up as night falls, not at dawn.
        long night = KingdomTavern.NIGHT_START;
        helper.assertTrue(KingdomTavern.nightOf(night - 1) + 1 == KingdomTavern.nightOf(night), "night didn't fall at nightfall");
        helper.assertTrue(KingdomTavern.nightOf(night) == KingdomTavern.nightOf(24000L + night - 1), "a new night started before the next nightfall");

        ServerLevel level = helper.getLevel();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Innsbury " + level.getGameTime(), DyeColor.ORANGE)).orElseThrow();
        Kingdoms.setLevel(level.getServer(), kingdom, 2);
        // Room for two: the Innkeeper and one traveller.
        BlockPos marker = TestHouses.build(level, houseCorner(hall), 2, true);
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x() + dx, center.z() + dz, true);
            }
        }
        BlockPos tavern = hall.east(3);
        helper.startSequence().thenWaitUntil(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(level.isPositionEntityTicking(new net.minecraft.world.level.ChunkPos(center.x() + dx, center.z() + dz).getMiddleBlockPosition(hall.getY())),
                            "forced chunks aren't loaded yet");
                }
            }
            // Wait for the light to spread through the house too.
            TestHouses.assertReady(level, marker);
        }).thenExecute(() -> {
            level.setBlockAndUpdate(tavern, ModBlocks.TAVERN.get().defaultBlockState());
            // A Tavern with no Innkeeper doesn't bring anyone.
            helper.assertTrue(KingdomTavern.nightfall(level.getServer(), kingdom).isEmpty(), "a villager moved in at night without an Innkeeper");

            // A villager of the kingdom takes the Tavern job.
            Villager innkeeper = KingdomVillagers.morning(level.getServer(), kingdom).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("no villager moved in"));
            innkeeper.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), tavern));
            innkeeper.setVillagerData(innkeeper.getVillagerData().setProfession(ModVillagers.INNKEEPER_PROFESSION.get()));
            helper.assertTrue(KingdomTavern.hasInnkeeper(level.getServer(), kingdom), "the Innkeeper isn't counted");

            Villager traveller = KingdomTavern.nightfall(level.getServer(), kingdom).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("no villager moved in at night"));
            helper.assertTrue(traveller.getTags().contains(KingdomVillagers.RESIDENT_TAG) && kingdom.residents().contains(traveller.getUUID()),
                    "the night traveller isn't a resident");

            // Still only into a free home: both beds are taken now.
            helper.assertTrue(KingdomTavern.nightfall(level.getServer(), kingdom).isEmpty(), "a villager moved in at night with no free home");

            // An Innkeeper whose Tavern is gone doesn't count.
            level.removeBlock(tavern, false);
            helper.assertFalse(KingdomTavern.hasInnkeeper(level.getServer(), kingdom), "an Innkeeper without a Tavern still counts");

            java.util.List.copyOf(kingdom.residents()).forEach(id -> java.util.Optional.ofNullable(level.getEntity(id))
                    .ifPresent(net.minecraft.world.entity.Entity::discard));
            Kingdoms.leave(leader);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setChunkForced(center.x() + dx, center.z() + dz, false);
                }
            }
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void villagersMoveIntoVacantHouses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        BlockPos hall = isolatedHall(helper);
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Housington " + level.getGameTime(), DyeColor.BROWN)).orElseThrow();
        helper.assertTrue(KingdomVillagers.morning(server, kingdom).isEmpty(), "a villager moved into a kingdom with no houses");

        // A house with a bed and a door, but no light: monsters could spawn inside, so it isn't a home yet.
        BlockPos corner = houseCorner(hall);
        BlockPos marker = TestHouses.build(level, corner, 1, false);
        House house = TestHouses.house(level, marker);
        HouseScan.Result scan = KingdomHouses.rescan(level, house);
        helper.assertTrue(scan.enclosed(), "the house isn't closed in: " + scan.opening() + " at " + scan.leak());
        helper.assertTrue(scan.beds().equals(java.util.List.of(corner.offset(2, 1, 1))), "the bed wasn't found: " + scan.beds());
        helper.assertTrue(scan.exteriorDoors() == 1, "the front door wasn't found, got " + scan.exteriorDoors());
        helper.assertFalse(scan.darkSpots().isEmpty(), "an unlit house has no dark spots");
        helper.assertFalse(house.valid() || KingdomHouses.homes(server, kingdom) != 0, "a dark house counts as a home");

        // Light it, and keep it loaded like a nearby player would.
        level.setBlockAndUpdate(corner.offset(2, 4, 2), net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        level.setChunkForced(center.x(), center.z(), true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.isPositionEntityTicking(marker), "the house's chunk isn't loaded yet");
            TestHouses.assertReady(level, marker);
        }).thenExecute(() -> {
            try {
                helper.assertTrue(KingdomHouses.homes(server, kingdom) == 1, "a one-bed house should be one home, got " + KingdomHouses.homes(server, kingdom));

                // A second marker in the same house doesn't make it two houses.
                BlockPos extra = corner.offset(3, 1, 1);
                level.setBlockAndUpdate(extra, ModBlocks.HOUSE_MARKER.get().defaultBlockState());
                House second = TestHouses.house(level, extra);
                KingdomHouses.rescan(level, second);
                helper.assertFalse(second.valid(), "a second marker in a house counted");
                helper.assertTrue(KingdomHouses.homes(server, kingdom) == 1, "two markers made two homes");
                level.removeBlock(extra, false);
                helper.assertTrue(KingdomHouses.at(server, GlobalPos.of(level.dimension(), extra)).isEmpty(), "a removed marker is still a house");

                // Someone moves in, inside the house, and makes its bed their own.
                Villager first = KingdomVillagers.morning(server, kingdom).orElseThrow(
                        () -> new net.minecraft.gametest.framework.GameTestAssertException("no villager moved into the house"));
                BlockPos at = first.blockPosition();
                helper.assertTrue(at.getX() > corner.getX() && at.getX() < corner.getX() + 4 && at.getZ() > corner.getZ()
                        && at.getZ() < corner.getZ() + 4, "the villager turned up outside the house, at " + at);
                helper.assertTrue(house.occupants().equals(java.util.List.of(first.getUUID())), "the villager wasn't given the house");
                helper.assertTrue(first.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
                        .filter(GlobalPos.of(level.dimension(), corner.offset(2, 1, 1))::equals).isPresent(), "the villager wasn't given the bed");
                helper.assertTrue(KingdomVillagers.morning(server, kingdom).isEmpty(), "a villager moved into a full house");

                // A hole in the roof: open to the sky. The villager keeps the house, but it's no home until it's fixed.
                BlockPos roof = corner.offset(1, 4, 2);
                level.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                HouseScan.Result open = KingdomHouses.rescan(level, house);
                helper.assertTrue(open.opening() == HouseScan.Opening.SKY && !house.valid(), "a hole in the roof wasn't noticed: " + open.opening());
                helper.assertTrue(house.occupants().contains(first.getUUID()), "the villager lost the house to a hole in the roof");
                level.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());

                // Walling up the door: closed in, but no way in or out.
                BlockPos door = corner.offset(4, 1, 2);
                level.setBlockAndUpdate(door, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(door.above(), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                HouseScan.Result walled = KingdomHouses.rescan(level, house);
                helper.assertTrue(walled.enclosed() && walled.exteriorDoors() == 0 && !house.valid(), "a house without a door counts");
                TestHouses.door(level, door, net.minecraft.core.Direction.EAST);
                TestHouses.assertReady(level, marker);

                // The villager dies: its bed is free for someone new.
                first.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                helper.assertTrue(house.occupants().isEmpty(), "a dead villager still lives in the house");
                Villager next = KingdomVillagers.morning(server, kingdom).orElseThrow(
                        () -> new net.minecraft.gametest.framework.GameTestAssertException("nobody moved in after the villager died"));

                // The marker is taken down: the villager is homeless and nobody new comes.
                level.removeBlock(marker, false);
                helper.assertTrue(KingdomHouses.homes(server, kingdom) == 0, "a house without its marker still counts");
                helper.assertTrue(KingdomVillagers.morning(server, kingdom).isEmpty(), "a villager moved in with no houses");
                // Put back, the homeless villager gets it first.
                level.setBlockAndUpdate(marker, ModBlocks.HOUSE_MARKER.get().defaultBlockState());
                House rebuilt = TestHouses.house(level, marker);
                helper.assertTrue(KingdomVillagers.morning(server, kingdom).isEmpty(), "a newcomer took the homeless villager's house");
                helper.assertTrue(rebuilt.valid() && rebuilt.occupants().equals(java.util.List.of(next.getUUID())),
                        "the homeless villager didn't get the house back: " + rebuilt.occupants());

                // Houses are saved.
                var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                House loaded = House.CODEC.parse(ops, House.CODEC.encodeStart(ops, rebuilt).getOrThrow()).getOrThrow();
                helper.assertTrue(loaded.pos().equals(rebuilt.pos()) && loaded.valid() && loaded.beds().equals(rebuilt.beds())
                        && loaded.occupants().equals(rebuilt.occupants()), "the house wasn't saved properly");
                next.discard();
            } finally {
                java.util.List.copyOf(kingdom.residents()).forEach(id -> java.util.Optional.ofNullable(level.getEntity(id))
                        .ifPresent(net.minecraft.world.entity.Entity::discard));
                Kingdoms.leave(leader);
                level.setChunkForced(center.x(), center.z(), false);
            }
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void housesTakeInRoomsBehindInsideDoors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = isolatedSpot(helper);
        // Two rooms side by side, 3 by 3 by 3 inside each: a front room with the way out and the marker, a back room
        // through an inside door with the bed. Each has a light.
        TestHouses.box(level, corner, 9, 5, 5);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                level.setBlockAndUpdate(corner.offset(4, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        TestHouses.door(level, corner.offset(0, 1, 2), net.minecraft.core.Direction.WEST);
        TestHouses.door(level, corner.offset(4, 1, 2), net.minecraft.core.Direction.EAST);
        TestHouses.bed(level, corner.offset(6, 1, 1), net.minecraft.core.Direction.EAST);
        level.setBlockAndUpdate(corner.offset(2, 4, 2), net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(corner.offset(6, 4, 2), net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
        BlockPos marker = corner.offset(2, 1, 3);
        level.setBlockAndUpdate(marker, ModBlocks.HOUSE_MARKER.get().defaultBlockState());

        HouseScan.Result scan = HouseScan.scan(level, marker);
        helper.assertTrue(scan.enclosed(), "the house isn't closed in: " + scan.opening() + " at " + scan.leak());
        helper.assertTrue(scan.space() > 27, "the back room wasn't taken in, only " + scan.space() + " blocks of air");
        helper.assertTrue(scan.beds().equals(java.util.List.of(corner.offset(7, 1, 1))), "the bed in the back room wasn't found: " + scan.beds());
        helper.assertTrue(scan.exteriorDoors() == 1, "the inside door counted as a way out, or the front door didn't: " + scan.exteriorDoors());
        // Once the light has spread, it's a home.
        helper.succeedWhen(() -> helper.assertTrue(HouseScan.scan(level, marker).valid(), "the two-room house isn't a home"));
    }
}
