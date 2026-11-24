package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import com.luckyowlstudios.kingdoms.network.CreateKingdomPayload;
import com.luckyowlstudios.kingdoms.network.OpenCreateKingdomPayload;
import com.luckyowlstudios.kingdoms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Kingdom helpers and the server side of founding one. Server side only.
 */
public final class Kingdoms {

    /** How close a player has to be to a Town Hall to found a kingdom there. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private Kingdoms() {
    }

    public static Optional<Kingdom> ofPlayer(ServerPlayer player) {
        return KingdomData.get(player.server).ofPlayer(player.getUUID());
    }

    public static Optional<Kingdom> ofPlayer(MinecraftServer server, UUID player) {
        return KingdomData.get(server).ofPlayer(player);
    }

    /** The kingdom whose seat is this Town Hall, if it has been set up. */
    public static Optional<Kingdom> atTownHall(MinecraftServer server, GlobalPos pos) {
        return KingdomData.get(server).atTownHall(pos);
    }

    /** The kingdom that has claimed the chunk this block is in, if any. */
    public static Optional<Kingdom> claimedBy(MinecraftServer server, GlobalPos pos) {
        return KingdomData.get(server).claimedBy(ChunkClaim.of(pos));
    }

    /** A player right-clicked a Town Hall. */
    public static void onTownHallUsed(ServerPlayer player, BlockPos pos) {
        GlobalPos hall = GlobalPos.of(player.level().dimension(), pos);
        Optional<Kingdom> owner = atTownHall(player.server, hall);
        if (owner.isPresent()) {
            Kingdom kingdom = owner.get();
            if (kingdom.isMember(player.getUUID())) {
                KingdomScreens.open(player, kingdom);
                TownHallBlockEntity.onScreenOpened(player, pos);
            } else if (ofPlayer(player).isEmpty() && !kingdom.isBanned(player.getUUID())
                    && (!kingdom.settings().inviteOnly() || kingdom.isInvited(player.getUUID()))) {
                player.sendSystemMessage(Component.translatable("kingdoms.kingdom.town_hall_of.open", kingdom.displayName(), joinButton(kingdom)));
            } else {
                player.displayClientMessage(Component.translatable("kingdoms.kingdom.town_hall_of", kingdom.displayName()), true);
            }
            return;
        }
        Optional<Kingdom> own = ofPlayer(player);
        Optional<Kingdom> landOwner = claimedBy(player.server, hall).filter(other -> own.map(k -> k != other).orElse(true));
        if (landOwner.isPresent()) {
            // Another kingdom's land: no founding or moving in here.
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.chunk_claimed", landOwner.get().displayName()), true);
            return;
        }
        if (own.isPresent() && own.get().townHall().isEmpty() && own.get().isLeader(player.getUUID())) {
            // A kingdom that lost its Town Hall can take this one as its new seat, along with the chunk it's in.
            KingdomData.get(player.server).setTownHall(own.get(), hall);
            TownHallBlockEntity.refresh(player.server, hall);
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.town_hall_claimed", own.get().displayName()), true);
            return;
        }
        if (own.isPresent()) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.already_member", own.get().displayName()), true);
            return;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(new OpenCreateKingdomPayload(pos)));
    }

    /** Cleans up a name typed by a player: trims it and collapses spaces. Empty if it isn't usable. */
    public static Optional<String> cleanName(String raw) {
        String name = raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > Kingdom.MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /** A player filled in the "Create Your Kingdom" screen. Checks everything again before founding it. */
    public static Optional<Kingdom> tryCreate(ServerPlayer player, CreateKingdomPayload request) {
        BlockPos pos = request.townHall();
        KingdomData data = KingdomData.get(player.server);
        GlobalPos hall = GlobalPos.of(player.level().dimension(), pos);
        if (!player.level().getBlockState(pos).is(ModBlocks.TOWN_HALL.get())
                || player.distanceToSqr(pos.getCenter()) > MAX_DISTANCE_SQR) {
            return Optional.empty();
        }
        if (data.atTownHall(hall).isPresent() || data.ofPlayer(player.getUUID()).isPresent()) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.cannot_found"), false);
            return Optional.empty();
        }
        Optional<Kingdom> landOwner = data.claimedBy(ChunkClaim.of(hall));
        if (landOwner.isPresent()) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.chunk_claimed", landOwner.get().displayName()), false);
            return Optional.empty();
        }
        Optional<String> name = cleanName(request.name());
        if (name.isEmpty()) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.invalid_name"), false);
            return Optional.empty();
        }
        if (data.isNameTaken(name.get())) {
            player.displayClientMessage(Component.translatable("kingdoms.kingdom.name_taken", name.get()), false);
            return Optional.empty();
        }
        DyeColor color = request.color();
        Kingdom kingdom = data.create(player.server, name.get(), color, player.getUUID(), hall);
        player.displayClientMessage(Component.translatable("kingdoms.kingdom.founded", kingdom.displayName()), false);
        return Optional.of(kingdom);
    }

    // ---- Membership, like vanilla teams ----

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    private static Optional<Kingdom> ledBy(ServerPlayer player) {
        return withRank(player, KingdomRank.LEADER);
    }

    /** The player's kingdom, if their rank in it is at least {@code needed}; tells them otherwise. */
    private static Optional<Kingdom> withRank(ServerPlayer player, KingdomRank needed) {
        Optional<Kingdom> kingdom = ofPlayer(player);
        if (kingdom.isEmpty()) {
            tell(player, "kingdoms.kingdom.not_in_kingdom");
            return Optional.empty();
        }
        if (!kingdom.get().rank(player.getUUID()).atLeast(needed)) {
            tell(player, needed == KingdomRank.LEADER ? "kingdoms.kingdom.not_leader" : "kingdoms.kingdom.not_officer",
                    kingdom.get().displayName());
            return Optional.empty();
        }
        return kingdom;
    }

    private static String nameOf(MinecraftServer server, UUID player) {
        return KingdomTeams.playerName(server, player).orElse(player.toString().substring(0, 8));
    }

    /** The leader invites someone. They can then join with {@code /kingdom join}. */
    public static boolean invite(ServerPlayer leader, ServerPlayer target) {
        Optional<Kingdom> kingdom = withRank(leader, KingdomRank.OFFICER);
        if (kingdom.isEmpty()) {
            return false;
        }
        if (kingdom.get().isBanned(target.getUUID())) {
            tell(leader, "kingdoms.kingdom.invite.banned", target.getDisplayName());
            return false;
        }
        KingdomData data = KingdomData.get(leader.server);
        if (data.ofPlayer(target.getUUID()).isPresent()) {
            tell(leader, "kingdoms.kingdom.invite.already_in_one", target.getDisplayName());
            return false;
        }
        data.invite(kingdom.get(), target.getUUID());
        tell(leader, "kingdoms.kingdom.invite.sent", target.getDisplayName());
        Component join = joinButton(kingdom.get());
        target.sendSystemMessage(Component.translatable("kingdoms.kingdom.invite.received",
                leader.getDisplayName(), kingdom.get().displayName(), join));
        return true;
    }

    /** Joins a kingdom the player was invited to. */
    public static boolean join(ServerPlayer player, String kingdomName) {
        KingdomData data = KingdomData.get(player.server);
        if (data.ofPlayer(player.getUUID()).isPresent()) {
            tell(player, "kingdoms.kingdom.already_member", data.ofPlayer(player.getUUID()).get().displayName());
            return false;
        }
        Optional<Kingdom> kingdom = data.byName(kingdomName);
        if (kingdom.isEmpty()) {
            tell(player, "kingdoms.kingdom.unknown", kingdomName);
            return false;
        }
        if (kingdom.get().isBanned(player.getUUID())) {
            tell(player, "kingdoms.kingdom.join.banned", kingdom.get().displayName());
            return false;
        }
        if (kingdom.get().settings().inviteOnly() && !kingdom.get().isInvited(player.getUUID())) {
            tell(player, "kingdoms.kingdom.join.not_invited", kingdom.get().displayName());
            return false;
        }
        data.addMember(player.server, kingdom.get(), player.getUUID());
        broadcast(player.server, kingdom.get(), Component.translatable("kingdoms.kingdom.join.joined", player.getDisplayName(), kingdom.get().displayName()));
        return true;
    }

    /** The leader removes a member. */
    public static boolean kick(ServerPlayer leader, UUID target) {
        Optional<Kingdom> kingdom = withRank(leader, KingdomRank.OFFICER);
        if (kingdom.isEmpty()) {
            return false;
        }
        if (kingdom.get().isMember(target) && !outranks(kingdom.get(), leader.getUUID(), target)) {
            tell(leader, "kingdoms.kingdom.outranked", nameOf(leader.server, target));
            return false;
        }
        String targetName = KingdomTeams.playerName(leader.server, target).orElse(target.toString());
        if (target.equals(leader.getUUID())) {
            tell(leader, "kingdoms.kingdom.kick.self");
            return false;
        }
        if (!kingdom.get().isMember(target)) {
            tell(leader, "kingdoms.kingdom.kick.not_member", targetName, kingdom.get().displayName());
            return false;
        }
        KingdomData.get(leader.server).removeMember(leader.server, kingdom.get(), target);
        broadcast(leader.server, kingdom.get(), Component.translatable("kingdoms.kingdom.kick.kicked", targetName, kingdom.get().displayName()));
        ServerPlayer kicked = leader.server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            tell(kicked, "kingdoms.kingdom.kick.you_were_kicked", kingdom.get().displayName());
        }
        return true;
    }

    /** Leaves the player's kingdom. A leader hands over to the next member; the last one out disbands it. */
    public static boolean leave(ServerPlayer player) {
        Optional<Kingdom> kingdom = ofPlayer(player);
        if (kingdom.isEmpty()) {
            tell(player, "kingdoms.kingdom.not_in_kingdom");
            return false;
        }
        boolean disbanded = KingdomData.get(player.server).removeMember(player.server, kingdom.get(), player.getUUID());
        if (disbanded) {
            tell(player, "kingdoms.kingdom.leave.disbanded", kingdom.get().displayName());
        } else {
            tell(player, "kingdoms.kingdom.leave.left", kingdom.get().displayName());
            broadcast(player.server, kingdom.get(), Component.translatable("kingdoms.kingdom.leave.member_left",
                    player.getDisplayName(), kingdom.get().displayName()));
        }
        return true;
    }

    private static Component joinButton(Kingdom kingdom) {
        return Component.translatable("kingdoms.kingdom.invite.join_button").withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/kingdom join " + kingdom.name())));
    }

    private static boolean outranks(Kingdom kingdom, UUID actor, UUID target) {
        return kingdom.rank(actor).ordinal() > kingdom.rank(target).ordinal();
    }

    /** Leader only: Member to Officer, or Officer to Leader (the old leader becomes an officer). */
    public static boolean promote(ServerPlayer leader, UUID target) {
        Optional<Kingdom> kingdom = ledBy(leader);
        if (kingdom.isEmpty()) {
            return false;
        }
        KingdomRank rank = kingdom.get().rank(target);
        KingdomRank next = rank == KingdomRank.MEMBER ? KingdomRank.OFFICER : rank == KingdomRank.OFFICER ? KingdomRank.LEADER : null;
        if (next == null) {
            tell(leader, "kingdoms.kingdom.promote.cannot", nameOf(leader.server, target));
            return false;
        }
        KingdomData.get(leader.server).setRank(kingdom.get(), target, next);
        broadcast(leader.server, kingdom.get(), Component.translatable("kingdoms.kingdom.promote.done",
                nameOf(leader.server, target), Component.translatable(next.translationKey())));
        return true;
    }

    /** Leader only: Officer to Member. */
    public static boolean demote(ServerPlayer leader, UUID target) {
        Optional<Kingdom> kingdom = ledBy(leader);
        if (kingdom.isEmpty()) {
            return false;
        }
        if (kingdom.get().rank(target) != KingdomRank.OFFICER) {
            tell(leader, "kingdoms.kingdom.demote.cannot", nameOf(leader.server, target));
            return false;
        }
        KingdomData.get(leader.server).setRank(kingdom.get(), target, KingdomRank.MEMBER);
        broadcast(leader.server, kingdom.get(), Component.translatable("kingdoms.kingdom.demote.done",
                nameOf(leader.server, target), Component.translatable(KingdomRank.MEMBER.translationKey())));
        return true;
    }

    /** Officers and up: kicks the player if they're a member and stops them joining or being invited. */
    public static boolean ban(ServerPlayer actor, UUID target) {
        Optional<Kingdom> kingdom = withRank(actor, KingdomRank.OFFICER);
        if (kingdom.isEmpty()) {
            return false;
        }
        if (target.equals(actor.getUUID()) || (kingdom.get().isMember(target) && !outranks(kingdom.get(), actor.getUUID(), target))) {
            tell(actor, "kingdoms.kingdom.outranked", nameOf(actor.server, target));
            return false;
        }
        String name = nameOf(actor.server, target);
        KingdomData.get(actor.server).ban(actor.server, kingdom.get(), target);
        broadcast(actor.server, kingdom.get(), Component.translatable("kingdoms.kingdom.ban.done", name, kingdom.get().displayName()));
        ServerPlayer banned = actor.server.getPlayerList().getPlayer(target);
        if (banned != null) {
            tell(banned, "kingdoms.kingdom.ban.you_were_banned", kingdom.get().displayName());
        }
        return true;
    }

    public static boolean unban(ServerPlayer actor, UUID target) {
        Optional<Kingdom> kingdom = withRank(actor, KingdomRank.OFFICER);
        if (kingdom.isEmpty() || !kingdom.get().isBanned(target)) {
            return false;
        }
        KingdomData.get(actor.server).unban(kingdom.get(), target);
        tell(actor, "kingdoms.kingdom.unban.done", nameOf(actor.server, target));
        return true;
    }

    public static boolean setColor(ServerPlayer leader, DyeColor color) {
        Optional<Kingdom> kingdom = ledBy(leader);
        if (kingdom.isEmpty()) {
            return false;
        }
        KingdomData.get(leader.server).setColor(leader.server, kingdom.get(), color);
        return true;
    }

    public static boolean setSettings(ServerPlayer leader, Kingdom.Settings settings) {
        Optional<Kingdom> kingdom = ledBy(leader);
        if (kingdom.isEmpty()) {
            return false;
        }
        KingdomData.get(leader.server).setSettings(leader.server, kingdom.get(), settings);
        return true;
    }

    /** The leader sets the kingdom's banner from a banner item. */
    public static boolean setBanner(ServerPlayer player, ItemStack banner) {
        if (!(banner.getItem() instanceof BannerItem)) {
            tell(player, "kingdoms.kingdom.banner.not_a_banner");
            return false;
        }
        Optional<Kingdom> kingdom = ledBy(player);
        if (kingdom.isEmpty()) {
            return false;
        }
        KingdomData.get(player.server).setBanner(kingdom.get(), banner);
        tell(player, "kingdoms.kingdom.banner.set", kingdom.get().displayName());
        return true;
    }

    // ---- Levels ----

    /** Sets a kingdom's level outright (the operator command), growing or shrinking its land, and tells its members. */
    public static void setLevel(MinecraftServer server, Kingdom kingdom, int level) {
        int before = kingdom.level();
        KingdomData.get(server).setLevel(kingdom, level);
        onLevelChanged(server, kingdom, before);
    }

    /** Tells the members about a new level, and throws a celebration at the Town Hall if it went up. */
    static void onLevelChanged(MinecraftServer server, Kingdom kingdom, int before) {
        if (kingdom.level() == before) {
            return;
        }
        KingdomLevel info = kingdom.levelInfo();
        broadcast(server, kingdom, Component.translatable("kingdoms.kingdom.level.reached", kingdom.displayName(),
                info.level(), info.villagerCap(), kingdom.landCap()));
        if (kingdom.level() > before) {
            KingdomCelebration.start(kingdom);
        }
    }

    /** A Town Hall block was removed. Its kingdom carries on, without a seat until it gets a new one. */
    public static void onTownHallRemoved(MinecraftServer server, GlobalPos pos) {
        KingdomData data = KingdomData.get(server);
        data.atTownHall(pos).ifPresent(kingdom -> {
            data.clearTownHall(kingdom);
            broadcast(server, kingdom, Component.translatable("kingdoms.kingdom.town_hall_lost", kingdom.displayName()));
        });
    }

    static void broadcast(MinecraftServer server, Kingdom kingdom, Component message) {
        for (UUID member : kingdom.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(message);
            }
        }
    }
}
