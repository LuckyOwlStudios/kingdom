package com.luckyowlstudios.kingdoms.test;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.luckyowlstudios.kingdoms.kingdom.ChunkClaim;
import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.kingdom.KingdomData;
import com.luckyowlstudios.kingdoms.kingdom.KingdomMaps;
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
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = 4096 + NEXT_HALL.getAndIncrement() * 2;
        BlockPos pos = new BlockPos(chunkX * 16 + 4, origin.getY() + 2, 4096 * 16 + 4);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.TOWN_HALL.get().defaultBlockState());
        return pos;
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
        helper.assertTrue(KingdomMaps.scale(leader, kingdom) == 0, "map should start at scale 0");
        ItemStack maps = new ItemStack(net.minecraft.world.item.Items.MAP, 5);
        for (int i = 0; i < 5; i++) {
            KingdomMaps.grow(leader, kingdom, maps);
        }
        helper.assertTrue(KingdomMaps.scale(leader, kingdom) == KingdomMaps.MAX_SCALE, "map didn't grow to the max");
        var data = KingdomMaps.getOrCreate(leader, kingdom).orElseThrow();
        helper.assertTrue(data.centerX == hall.getX() && data.centerZ == hall.getZ(),
                "map should be centred on the town hall " + hall + ", not " + data.centerX + ", " + data.centerZ);
        helper.assertTrue(maps.getCount() == 1, "should use one empty map per step, " + maps.getCount() + " left");
        KingdomMaps.explore(leader);
        KingdomScreens.open(leader, kingdom);
        helper.succeed();
    }
}
