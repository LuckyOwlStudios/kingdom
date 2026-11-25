package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Which kingdom a work block (Scholar's Desk, Tavern, Watchtower) belongs to.
 * <p>
 * A work block placed by a kingdom member belongs to that kingdom, wherever it is. One placed by anyone else (or
 * before this existed) belongs to the kingdom whose land it's on, if any. A villager who takes the job at a work
 * block joins the kingdom it belongs to (see {@link KingdomVillagers#checkWorker}), and only counts as working for
 * that kingdom. Breaking the block forgets its owner. Server side only.
 */
public final class KingdomWorkstations {

    private KingdomWorkstations() {
    }

    /** The kingdom the work block at {@code pos} belongs to: whoever placed it, or else whose land it's on. */
    public static Optional<Kingdom> owner(MinecraftServer server, GlobalPos pos) {
        KingdomData data = KingdomData.get(server);
        return data.workstationOwner(pos).or(() -> data.claimedBy(ChunkClaim.of(pos)));
    }

    public static boolean belongsTo(MinecraftServer server, GlobalPos pos, Kingdom kingdom) {
        return owner(server, pos).filter(owner -> owner == kingdom).isPresent();
    }

    /** A work block was placed. If a kingdom member placed it, it's their kingdom's, and they're told so. */
    public static void onPlaced(Level level, BlockPos pos, BlockState state, LivingEntity placer) {
        if (!(placer instanceof ServerPlayer player)) {
            return;
        }
        GlobalPos at = GlobalPos.of(level.dimension(), pos);
        Kingdoms.ofPlayer(player).ifPresent(kingdom -> {
            KingdomData.get(player.server).setWorkstationOwner(at, kingdom);
            player.displayClientMessage(Component.translatable("kingdoms.workstation.placed",
                    state.getBlock().getName(), kingdom.displayName()), true);
        });
    }

    /** A work block is gone (broken, blown up, replaced): it belongs to nobody now. */
    public static void onRemoved(Level level, BlockPos pos, BlockState state, BlockState newState) {
        if (level.getServer() != null && !state.is(newState.getBlock())) {
            KingdomData.get(level.getServer()).setWorkstationOwner(GlobalPos.of(level.dimension(), pos), null);
        }
    }
}
