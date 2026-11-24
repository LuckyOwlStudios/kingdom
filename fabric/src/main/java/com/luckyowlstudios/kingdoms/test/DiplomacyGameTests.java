package com.luckyowlstudios.kingdoms.test;

import com.luckyowlstudios.kingdoms.kingdom.Diplomacy;
import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.kingdom.KingdomData;
import com.luckyowlstudios.kingdoms.kingdom.KingdomDiplomacy;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

import java.util.concurrent.atomic.AtomicInteger;

/** Alliances and enemies between kingdoms. Run with {@code ./gradlew :fabric:runGametest}. */
public class DiplomacyGameTests implements FabricGameTest {

    private static final AtomicInteger NEXT_HALL = new AtomicInteger();

    /** A founded kingdom in a chunk of its own, on a row of chunks no other test class uses. */
    private static Kingdom found(GameTestHelper helper, ServerPlayer leader, String name, DyeColor color) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = 4096 + NEXT_HALL.getAndIncrement() * 2;
        BlockPos pos = new BlockPos(chunkX * 16 + 4, origin.getY() + 2, 6144 * 16 + 4);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.TOWN_HALL.get().defaultBlockState());
        leader.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        return Kingdoms.tryCreate(leader, new CreateKingdomPayload(pos, name, color)).orElseThrow();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void alliancesNeedBothSides(GameTestHelper helper) {
        long time = helper.getLevel().getGameTime();
        ServerPlayer leaderA = helper.makeMockServerPlayerInLevel();
        ServerPlayer leaderB = helper.makeMockServerPlayerInLevel();
        ServerPlayer memberB = helper.makeMockServerPlayerInLevel();
        Kingdom a = found(helper, leaderA, "Allyton " + time, DyeColor.BLUE);
        Kingdom b = found(helper, leaderB, "Befriend " + time, DyeColor.YELLOW);
        Kingdoms.invite(leaderB, memberB);
        helper.assertTrue(Kingdoms.join(memberB, b.name()), "member couldn't join");
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(leaderA.server);

        helper.assertFalse(Diplomacy.requestAlliance(leaderA, a), "a kingdom asked itself for an alliance");
        helper.assertTrue(Diplomacy.requestAlliance(leaderA, b), "leader couldn't ask for an alliance");
        helper.assertTrue(diplomacy.hasRequested(a.id(), b.id()), "request wasn't recorded");
        helper.assertFalse(diplomacy.areAllied(a.id(), b.id()), "allied before the other side accepted");
        helper.assertFalse(Diplomacy.requestAlliance(leaderA, b), "asked twice");

        DiplomacyViewPayload viewB = Diplomacy.view(leaderB, b);
        helper.assertTrue(viewB.canManage(), "leader can't manage diplomacy");
        helper.assertTrue(viewB.kingdoms().stream().anyMatch(entry -> entry.id().equals(a.id()) && entry.requestReceived()),
                "request doesn't show as pending for the other kingdom");
        helper.assertFalse(viewB.kingdoms().stream().anyMatch(entry -> entry.id().equals(b.id())), "own kingdom listed");
        helper.assertFalse(Diplomacy.view(memberB, b).canManage(), "a plain member can manage diplomacy");

        helper.assertFalse(Diplomacy.accept(memberB, a), "a plain member accepted an alliance");
        helper.assertTrue(Diplomacy.decline(leaderB, a), "couldn't decline");
        helper.assertFalse(diplomacy.hasRequested(a.id(), b.id()), "declined request is still pending");
        helper.assertFalse(Diplomacy.accept(leaderB, a), "accepted a request that was declined");

        Diplomacy.requestAlliance(leaderA, b);
        helper.assertTrue(Diplomacy.cancelRequest(leaderA, b), "couldn't cancel the request");
        helper.assertFalse(diplomacy.hasRequested(a.id(), b.id()), "cancelled request is still pending");

        Diplomacy.requestAlliance(leaderA, b);
        helper.assertTrue(Diplomacy.accept(leaderB, a), "couldn't accept");
        helper.assertTrue(diplomacy.areAllied(a.id(), b.id()) && diplomacy.areAllied(b.id(), a.id()), "not allied after accepting");
        helper.assertFalse(diplomacy.hasRequested(a.id(), b.id()), "request wasn't used up");

        helper.assertTrue(Diplomacy.endAlliance(leaderB, a), "the side that accepted couldn't end the alliance");
        helper.assertFalse(diplomacy.areAllied(a.id(), b.id()), "still allied after ending it");

        // Asking a kingdom that has already asked you makes the alliance straight away.
        Diplomacy.requestAlliance(leaderA, b);
        helper.assertTrue(Diplomacy.requestAlliance(leaderB, a), "crossing request failed");
        helper.assertTrue(diplomacy.areAllied(a.id(), b.id()), "crossing requests didn't make an alliance");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enemiesAreOneSided(GameTestHelper helper) {
        long time = helper.getLevel().getGameTime();
        ServerPlayer leaderA = helper.makeMockServerPlayerInLevel();
        ServerPlayer leaderB = helper.makeMockServerPlayerInLevel();
        Kingdom a = found(helper, leaderA, "Rivalia " + time, DyeColor.RED);
        Kingdom b = found(helper, leaderB, "Foeburg " + time, DyeColor.BLACK);
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(leaderA.server);

        Diplomacy.requestAlliance(leaderA, b);
        Diplomacy.accept(leaderB, a);
        helper.assertTrue(Diplomacy.declareEnemy(leaderA, b), "couldn't declare an enemy");
        helper.assertFalse(diplomacy.areAllied(a.id(), b.id()), "declaring an enemy didn't end the alliance");
        helper.assertTrue(diplomacy.hasDeclaredEnemy(a.id(), b.id()), "enemy wasn't recorded");
        helper.assertFalse(diplomacy.hasDeclaredEnemy(b.id(), a.id()), "enemy declaration should be one-sided");
        helper.assertTrue(diplomacy.areHostile(b.id(), a.id()), "hostility should show from both sides");
        helper.assertFalse(Diplomacy.declareEnemy(leaderA, b), "declared the same enemy twice");
        helper.assertFalse(Diplomacy.requestAlliance(leaderA, b), "asked an enemy for an alliance without making peace");
        helper.assertFalse(Diplomacy.makePeace(leaderB, a), "made peace without having declared an enemy");

        helper.assertTrue(Diplomacy.makePeace(leaderA, b), "couldn't make peace");
        helper.assertFalse(diplomacy.areHostile(a.id(), b.id()), "still hostile after making peace");

        // An alliance clears any enemy declarations between the two.
        Diplomacy.declareEnemy(leaderB, a);
        helper.assertTrue(Diplomacy.requestAlliance(leaderA, b), "couldn't ask a kingdom that counts you an enemy");
        helper.assertTrue(Diplomacy.accept(leaderB, a), "couldn't accept");
        helper.assertFalse(diplomacy.areHostile(a.id(), b.id()), "alliance didn't clear the enemy declaration");

        // Disbanding drops every relation.
        Diplomacy.declareEnemy(leaderA, b);
        Kingdoms.leave(leaderB);
        helper.assertTrue(KingdomData.get(leaderA.server).get(b.id()).isEmpty(), "kingdom wasn't disbanded");
        helper.assertFalse(diplomacy.areHostile(a.id(), b.id()) || diplomacy.areAllied(a.id(), b.id()),
                "a disbanded kingdom kept its relations");
        helper.succeed();
    }
}
