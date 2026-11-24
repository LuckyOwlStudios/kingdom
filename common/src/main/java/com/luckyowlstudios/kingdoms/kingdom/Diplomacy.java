package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.network.DiplomacyActionPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Alliances and enemies between kingdoms: the server side of the Diplomacy tab and {@code /kingdom diplomacy}.
 * Officers and the leader speak for their kingdom. Every change is announced to the online members of both
 * kingdoms. Relations have no effect on play yet. Server side only.
 */
public final class Diplomacy {

    /** The rank needed to change how the kingdom stands with others. */
    public static final KingdomRank RANK_NEEDED = KingdomRank.OFFICER;

    private Diplomacy() {
    }

    public static boolean canManage(Kingdom kingdom, UUID player) {
        return kingdom.rank(player).atLeast(RANK_NEEDED);
    }

    // ---- Actions ----

    /** Asks another kingdom for an alliance. If they had already asked us, the alliance is made at once. */
    public static boolean requestAlliance(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(player.server);
        if (diplomacy.areAllied(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.already_allied", target.displayName());
            return false;
        }
        if (diplomacy.hasDeclaredEnemy(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.request.at_war", target.displayName());
            return false;
        }
        if (diplomacy.hasRequested(target.id(), us.id())) {
            return accept(player, target);
        }
        if (diplomacy.hasRequested(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.request.already_sent", target.displayName());
            return false;
        }
        diplomacy.request(us.id(), target.id());
        broadcast(player.server, us, Component.translatable("kingdoms.diplomacy.request.sent",
                player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.request.received",
                us.displayName(), answerButtons(us)));
        sync(player.server, us, target);
        return true;
    }

    /** Takes back an alliance request that hasn't been answered. */
    public static boolean cancelRequest(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        if (!KingdomDiplomacy.get(player.server).removeRequest(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.request.none_sent", target.displayName());
            return false;
        }
        broadcast(player.server, us, Component.translatable("kingdoms.diplomacy.request.cancelled",
                player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.request.withdrawn", us.displayName()));
        sync(player.server, us, target);
        return true;
    }

    /** Accepts another kingdom's alliance request. Any enemy declarations between the two are dropped. */
    public static boolean accept(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(player.server);
        if (!diplomacy.hasRequested(target.id(), us.id())) {
            tell(player, "kingdoms.diplomacy.request.none_received", target.displayName());
            return false;
        }
        diplomacy.ally(us.id(), target.id());
        Component message = Component.translatable("kingdoms.diplomacy.allied", us.displayName(), target.displayName());
        broadcast(player.server, us, message);
        broadcast(player.server, target, message);
        sync(player.server, us, target);
        return true;
    }

    /** Turns down another kingdom's alliance request. */
    public static boolean decline(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        if (!KingdomDiplomacy.get(player.server).removeRequest(target.id(), us.id())) {
            tell(player, "kingdoms.diplomacy.request.none_received", target.displayName());
            return false;
        }
        broadcast(player.server, us, Component.translatable("kingdoms.diplomacy.request.declined",
                player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.request.declined_by", us.displayName()));
        sync(player.server, us, target);
        return true;
    }

    /** Ends an alliance. Either side can. */
    public static boolean endAlliance(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        if (!KingdomDiplomacy.get(player.server).endAlliance(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.not_allied", target.displayName());
            return false;
        }
        broadcast(player.server, us, Component.translatable("kingdoms.diplomacy.alliance_ended",
                player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.alliance_ended_by", us.displayName()));
        sync(player.server, us, target);
        return true;
    }

    /** Declares another kingdom an enemy. This needs no answer, and ends any alliance between them. */
    public static boolean declareEnemy(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(player.server);
        if (diplomacy.hasDeclaredEnemy(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.already_enemy", target.displayName());
            return false;
        }
        boolean wasAllied = diplomacy.areAllied(us.id(), target.id());
        diplomacy.declareEnemy(us.id(), target.id());
        broadcast(player.server, us, Component.translatable(wasAllied ? "kingdoms.diplomacy.enemy.declared_on_ally"
                : "kingdoms.diplomacy.enemy.declared", player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.enemy.declared_on_you",
                us.displayName()).withStyle(ChatFormatting.RED));
        sync(player.server, us, target);
        return true;
    }

    /** Takes back an enemy declaration this kingdom made. If the other kingdom declared one too, theirs stands. */
    public static boolean makePeace(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = speakerFor(player, target);
        if (own.isEmpty()) {
            return false;
        }
        Kingdom us = own.get();
        if (!KingdomDiplomacy.get(player.server).makePeace(us.id(), target.id())) {
            tell(player, "kingdoms.diplomacy.not_enemy", target.displayName());
            return false;
        }
        broadcast(player.server, us, Component.translatable("kingdoms.diplomacy.peace",
                player.getDisplayName(), target.displayName()));
        broadcast(player.server, target, Component.translatable("kingdoms.diplomacy.peace_by", us.displayName()));
        sync(player.server, us, target);
        return true;
    }

    /** A button in the Diplomacy tab. */
    public static void handle(ServerPlayer player, DiplomacyActionPayload action) {
        Optional<Kingdom> target = KingdomData.get(player.server).get(action.kingdom());
        if (target.isEmpty()) {
            Kingdoms.ofPlayer(player).ifPresent(own -> send(player, own));
            return;
        }
        switch (action.action()) {
            case REQUEST_ALLIANCE -> requestAlliance(player, target.get());
            case CANCEL_REQUEST -> cancelRequest(player, target.get());
            case ACCEPT_ALLIANCE -> accept(player, target.get());
            case DECLINE_ALLIANCE -> decline(player, target.get());
            case END_ALLIANCE -> endAlliance(player, target.get());
            case DECLARE_ENEMY -> declareEnemy(player, target.get());
            case MAKE_PEACE -> makePeace(player, target.get());
        }
        // Refresh the presser's screen even if nothing changed, so it never shows a stale state.
        Kingdoms.ofPlayer(player).ifPresent(own -> send(player, own));
    }

    // ---- Sync ----

    public static DiplomacyViewPayload view(ServerPlayer viewer, Kingdom kingdom) {
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(viewer.server);
        List<DiplomacyViewPayload.Entry> entries = new ArrayList<>();
        for (Kingdom other : KingdomData.get(viewer.server).all()) {
            if (other == kingdom) {
                continue;
            }
            entries.add(new DiplomacyViewPayload.Entry(other.id(), other.name(), other.color(),
                    diplomacy.areAllied(kingdom.id(), other.id()),
                    diplomacy.hasDeclaredEnemy(kingdom.id(), other.id()),
                    diplomacy.hasDeclaredEnemy(other.id(), kingdom.id()),
                    diplomacy.hasRequested(kingdom.id(), other.id()),
                    diplomacy.hasRequested(other.id(), kingdom.id())));
        }
        // Requests waiting for an answer first, then allies, then enemies, then everyone else; by name within each.
        entries.sort(Comparator.comparingInt(Diplomacy::sortGroup)
                .thenComparing(entry -> entry.name().toLowerCase(java.util.Locale.ROOT)));
        return new DiplomacyViewPayload(canManage(kingdom, viewer.getUUID()), entries);
    }

    private static int sortGroup(DiplomacyViewPayload.Entry entry) {
        if (entry.requestReceived()) return 0;
        if (entry.allied()) return 1;
        if (entry.weDeclaredEnemy() || entry.theyDeclaredEnemy()) return 2;
        return 3;
    }

    /** Sends a member their kingdom's relations. */
    public static void send(ServerPlayer player, Kingdom kingdom) {
        player.connection.send(new ClientboundCustomPayloadPacket(view(player, kingdom)));
    }

    /** Keeps open Diplomacy tabs current for everyone in both kingdoms. */
    private static void sync(MinecraftServer server, Kingdom... kingdoms) {
        for (Kingdom kingdom : kingdoms) {
            for (UUID member : kingdom.members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (online != null) {
                    send(online, kingdom);
                }
            }
        }
    }

    /** A kingdom was disbanded: drop its relations and update everyone who had one with it. */
    static void onDisbanded(MinecraftServer server, Kingdom kingdom) {
        KingdomDiplomacy.get(server).forget(kingdom.id());
        sync(server, KingdomData.get(server).all().toArray(Kingdom[]::new));
    }

    // ---- Helpers ----

    /** The player's kingdom, if they can speak for it and the target is another kingdom; tells them otherwise. */
    private static Optional<Kingdom> speakerFor(ServerPlayer player, Kingdom target) {
        Optional<Kingdom> own = Kingdoms.ofPlayer(player);
        if (own.isEmpty()) {
            tell(player, "kingdoms.kingdom.not_in_kingdom");
            return Optional.empty();
        }
        if (!canManage(own.get(), player.getUUID())) {
            tell(player, "kingdoms.kingdom.not_officer", own.get().displayName());
            return Optional.empty();
        }
        if (own.get() == target || own.get().id().equals(target.id())) {
            tell(player, "kingdoms.diplomacy.self");
            return Optional.empty();
        }
        return own;
    }

    private static Component answerButtons(Kingdom asking) {
        return Component.translatable("kingdoms.diplomacy.button.accept").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kingdom diplomacy accept " + asking.name()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("kingdoms.diplomacy.button.accept.tooltip", asking.displayName()))))
                .append(" ")
                .append(Component.translatable("kingdoms.diplomacy.button.decline").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kingdom diplomacy decline " + asking.name()))));
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    private static void broadcast(MinecraftServer server, Kingdom kingdom, Component message) {
        for (UUID member : kingdom.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(message);
            }
        }
    }
}
