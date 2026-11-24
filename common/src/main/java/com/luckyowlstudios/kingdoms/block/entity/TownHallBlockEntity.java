package com.luckyowlstudios.kingdoms.block.entity;

import com.luckyowlstudios.kingdoms.kingdom.Kingdom;
import com.luckyowlstudios.kingdoms.kingdom.Kingdoms;
import com.luckyowlstudios.kingdoms.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Town Hall's block entity. Kingdoms themselves are saved in the world's {@code data/kingdoms.dat}; this only
 * mirrors what the client needs to draw the hall: the colour of the kingdom seated here (none if the hall is free),
 * and whether anyone has the Town Hall screen open, which opens the book on top.
 * <p>
 * Nothing here is saved: the server works it out again from the kingdoms when the hall loads.
 */
public class TownHallBlockEntity extends BlockEntity {

    /** How often the server re-checks the kingdom and drops viewers who wandered off or logged out. */
    private static final int RECHECK_INTERVAL = 20;
    /** A viewer farther away than this has stopped reading. Matches how close you need to be to found a kingdom. */
    private static final double MAX_VIEW_DISTANCE_SQR = 8.0 * 8.0;
    /** How much the book opens or closes each tick: fully open in half a second. */
    private static final float OPEN_SPEED = 0.1F;

    private static final String KINGDOM_COLOR_TAG = "KingdomColor";
    private static final String BOOK_OPEN_TAG = "BookOpen";

    // Synced to the client.
    @Nullable
    private DyeColor kingdomColor;
    private boolean bookOpen;

    // Server only: players with this hall's Town Hall screen open.
    private final Set<UUID> viewers = new HashSet<>();
    private int recheckTimer;

    // Client only: book animation, 0 closed to 1 open.
    private float open;
    private float oOpen;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL.get(), pos, state);
    }

    /** The colour of the kingdom whose seat this is, or null if the hall doesn't belong to a kingdom. */
    @Nullable
    public DyeColor kingdomColor() {
        return this.kingdomColor;
    }

    public boolean hasKingdom() {
        return this.kingdomColor != null;
    }

    /** True while someone in the kingdom has this hall's Town Hall screen open. */
    public boolean isBookOpen() {
        return this.bookOpen;
    }

    /** How far open the book is, 0 (closed) to 1 (open), between ticks. */
    public float bookOpenness(float partialTick) {
        return Mth.lerp(partialTick, this.oOpen, this.open);
    }

    // ---- Server ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
        if (--hall.recheckTimer <= 0) {
            hall.recheckTimer = RECHECK_INTERVAL;
            hall.pruneViewers();
            hall.refresh();
        }
    }

    /** Looks up this hall's kingdom and viewers again, and tells nearby clients if anything changed. */
    public void refresh() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        Optional<Kingdom> kingdom = Kingdoms.atTownHall(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        DyeColor color = kingdom.map(Kingdom::color).orElse(null);
        if (color == null) {
            this.viewers.clear();
        }
        boolean open = color != null && !this.viewers.isEmpty();
        if (color != this.kingdomColor || open != this.bookOpen) {
            this.kingdomColor = color;
            this.bookOpen = open;
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void pruneViewers() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.viewers.removeIf(id -> {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
            return player == null || player.isRemoved() || player.level() != serverLevel
                    || player.distanceToSqr(this.worldPosition.getCenter()) > MAX_VIEW_DISTANCE_SQR;
        });
    }

    /** Refreshes the Town Hall at {@code pos}, if it's loaded. Call after a kingdom gains, loses or recolours its hall. */
    public static void refresh(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null && level.isLoaded(pos.pos()) && level.getBlockEntity(pos.pos()) instanceof TownHallBlockEntity hall) {
            hall.refresh();
        }
    }

    /** The player opened this hall's Town Hall screen: open the book. */
    public static void onScreenOpened(ServerPlayer player, BlockPos pos) {
        if (player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            hall.viewers.add(player.getUUID());
            hall.refresh();
        }
    }

    /** The player closed the Town Hall screen: close the book once nobody else is reading. */
    public static void onScreenClosed(ServerPlayer player, BlockPos pos) {
        if (player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof TownHallBlockEntity hall
                && hall.viewers.remove(player.getUUID())) {
            hall.refresh();
        }
    }

    // ---- Client ----

    public static void clientTick(Level level, BlockPos pos, BlockState state, TownHallBlockEntity hall) {
        hall.oOpen = hall.open;
        hall.open = Mth.clamp(hall.open + (hall.bookOpen ? OPEN_SPEED : -OPEN_SPEED), 0.0F, 1.0F);
    }

    // ---- Syncing ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KINGDOM_COLOR_TAG, this.kingdomColor == null ? -1 : this.kingdomColor.getId());
        tag.putBoolean(BOOK_OPEN_TAG, this.bookOpen);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KINGDOM_COLOR_TAG)) {
            int id = tag.getInt(KINGDOM_COLOR_TAG);
            DyeColor color = id < 0 ? null : DyeColor.byId(id);
            if (color != this.kingdomColor) {
                this.kingdomColor = color;
                if (this.level != null && this.level.isClientSide()) {
                    // The cloth's tint is baked into the chunk mesh, so redraw the block.
                    this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
                }
            }
        }
        if (tag.contains(BOOK_OPEN_TAG)) {
            this.bookOpen = tag.getBoolean(BOOK_OPEN_TAG);
        }
    }
}
