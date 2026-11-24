package com.luckyowlstudios.kingdoms.test;

import com.luckyowlstudios.kingdoms.kingdom.ChunkClaim;
import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.kingdom.KingdomVillagers;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.kingdom.Watchtowers;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import com.luckyowlstudios.kingdoms.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** The Watchtower and its Lookout. Run with {@code ./gradlew :fabric:runGametest}. */
public class WatchtowerGameTests implements FabricGameTest {

    private static final AtomicInteger NEXT_HALL = new AtomicInteger();

    @GameTest(template = EMPTY_STRUCTURE)
    public void watchtowerIsAJobSite(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.WATCHTOWER.get());
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getPoiManager().getType(helper.absolutePos(pos))
                .map(type -> type.is(ModVillagers.WATCHTOWER_POI)).orElse(false), "the Watchtower isn't a point of interest"));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void lookoutWarnsAboutTradersAndPatrolsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // A Town Hall in a chunk of its own, on a row of chunks no other test class uses.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos hall = new BlockPos((4096 + NEXT_HALL.getAndIncrement() * 8) * 16 + 4, origin.getY() + 2, 10240 * 16 + 4);
        level.setBlockAndUpdate(hall, ModBlocks.TOWN_HALL.get().defaultBlockState());
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.moveTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
        Kingdom kingdom = Kingdoms.tryCreate(leader, new CreateKingdomPayload(hall, "Lookoutburg " + level.getGameTime(), DyeColor.GREEN)).orElseThrow();
        BlockPos tower = hall.offset(3, 0, 0);
        level.setBlockAndUpdate(tower, ModBlocks.WATCHTOWER.get().defaultBlockState());
        // Keep the land loaded, like a nearby player would.
        ChunkClaim center = ChunkClaim.of(GlobalPos.of(level.dimension(), hall));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x() + dx, center.z() + dz, true);
            }
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(level.isPositionEntityTicking(new ChunkPos(center.x() + dx, center.z() + dz).getMiddleBlockPosition(hall.getY())),
                            "forced chunks aren't loaded yet");
                }
            }
        }).thenExecute(() -> {
            List<Entity> spawned = new ArrayList<>();
            try {
                Entity trader = spawn(level, EntityType.WANDERING_TRADER, tower.offset(8, 0, 0), null, spawned);
                // Nobody on watch yet: nothing is seen.
                helper.assertTrue(Watchtowers.look(server, kingdom).isEmpty(), "a Watchtower without a Lookout raised an alert");

                Villager lookout = KingdomVillagers.morning(server, kingdom).orElseThrow(() -> new GameTestAssertException("no villager moved in"));
                spawned.add(lookout);
                lookout.setVillagerData(lookout.getVillagerData().setProfession(ModVillagers.LOOKOUT_PROFESSION.get()));
                lookout.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), tower));
                helper.assertTrue(Watchtowers.mannedTowers(server, kingdom).size() == 1, "the Lookout isn't on watch");

                // A patrol of three, and a lone pillager that isn't on patrol (like one guarding an outpost).
                for (int i = 0; i < 3; i++) {
                    spawn(level, EntityType.PILLAGER, tower.offset(-12 + i, 0, 6), MobSpawnType.PATROL, spawned);
                }
                spawn(level, EntityType.PILLAGER, tower.offset(0, 0, -14), MobSpawnType.STRUCTURE, spawned);

                List<Watchtowers.Alert> alerts = Watchtowers.look(server, kingdom);
                helper.assertTrue(alerts.size() == 2, "expected one trader and one patrol alert, got " + alerts);
                Watchtowers.Alert traderAlert = alerts.stream().filter(a -> a.sighting() == Watchtowers.WANDERING_TRADER).findFirst()
                        .orElseThrow(() -> new GameTestAssertException("the wandering trader wasn't reported"));
                Watchtowers.Alert patrolAlert = alerts.stream().filter(a -> a.sighting() == Watchtowers.ILLAGER_PATROL).findFirst()
                        .orElseThrow(() -> new GameTestAssertException("the patrol wasn't reported"));
                helper.assertTrue(traderAlert.count() == 1 && traderAlert.pos().equals(trader.blockPosition()),
                        "the trader alert has the wrong count or place: " + traderAlert);
                helper.assertTrue(patrolAlert.count() == 3, "the patrol should be one alert for three illagers, got " + patrolAlert.count());
                helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.BLUE).equals(traderAlert.message().getStyle().getColor()),
                        "the trader alert isn't blue");
                helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(patrolAlert.message().getStyle().getColor()),
                        "the patrol alert isn't red");

                // Each is only reported once.
                helper.assertTrue(Watchtowers.look(server, kingdom).isEmpty(), "the same things were reported twice");

                // Without its Watchtower, the Lookout isn't on watch.
                level.removeBlock(tower, false);
                helper.assertTrue(Watchtowers.mannedTowers(server, kingdom).isEmpty(), "a Lookout without a Watchtower is on watch");
            } finally {
                Kingdoms.leave(leader);
                spawned.forEach(Entity::discard);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setChunkForced(center.x() + dx, center.z() + dz, false);
                    }
                }
            }
        }).thenSucceed();
    }

    private static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos, MobSpawnType reason, List<Entity> spawned) {
        T entity = type.create(level);
        if (entity == null) {
            throw new GameTestAssertException("couldn't create " + type);
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        if (reason != null && entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, null);
        }
        level.addFreshEntity(entity);
        spawned.add(entity);
        return entity;
    }
}
