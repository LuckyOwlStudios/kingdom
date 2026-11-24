package com.luckyowlstudios.kingdoms.kingdom;

import com.luckyowlstudios.kingdoms.network.KingdomActionPayload;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of the Town Hall screen: builds what it shows and carries out its buttons.
 */
public final class KingdomScreens {

    private KingdomScreens() {
    }

    public static KingdomViewPayload view(ServerPlayer viewer, Kingdom kingdom) {
        List<KingdomViewPayload.Member> members = new ArrayList<>();
        // Leader, then officers, then members, then the ban list.
        kingdom.members().stream()
                .sorted((a, b) -> kingdom.rank(b).ordinal() - kingdom.rank(a).ordinal())
                .forEach(id -> members.add(new KingdomViewPayload.Member(id,
                        KingdomTeams.playerName(viewer.server, id).orElse("?"), kingdom.rank(id), false)));
        kingdom.banned().forEach(id -> members.add(new KingdomViewPayload.Member(id,
                KingdomTeams.playerName(viewer.server, id).orElse("?"), KingdomRank.NONE, true)));
        BlockPos hall = kingdom.townHall().map(pos -> pos.pos()).orElse(viewer.blockPosition());
        Optional<MapItemSavedData> map = KingdomMaps.getOrCreate(viewer, kingdom);
        // The map shows the Town Hall's dimension, so only that dimension's claims are drawn on it.
        List<ChunkPos> claims = kingdom.townHall().map(townHall -> kingdom.claims().stream()
                .filter(chunk -> chunk.dimension().equals(townHall.dimension())).map(ChunkClaim::chunkPos).toList()).orElse(List.of());
        return new KingdomViewPayload(hall, kingdom.name(), kingdom.color(), kingdom.banner().orElse(ItemStack.EMPTY),
                kingdom.map(), map.map(data -> (int) data.scale).orElse(0),
                map.map(data -> data.centerX).orElse(hall.getX()), map.map(data -> data.centerZ).orElse(hall.getZ()),
                claims, kingdom.claims().size(),
                kingdom.settings().inviteOnly(), kingdom.settings().friendlyFire(),
                kingdom.rank(viewer.getUUID()), members, growth(kingdom));
    }

    private static KingdomViewPayload.Growth growth(Kingdom kingdom) {
        KingdomLevel level = kingdom.levelInfo();
        int cost = level.isMax() ? 0 : KingdomLevel.of(level.level() + 1).cost();
        return new KingdomViewPayload.Growth(level.level(), KingdomLevel.MAX_LEVEL, kingdom.residents().size(),
                level.villagerCap(), level.landCap(), cost, KingdomPerks.availablePoints(kingdom), Map.copyOf(kingdom.perks()),
                kingdom.taxes(), TaxCollector.treasuryCap(viewer.server, kingdom));
    }

    /** Opens (or refreshes) the Town Hall screen for a member. */
    public static void open(ServerPlayer player, Kingdom kingdom) {
        KingdomMaps.getOrCreate(player, kingdom);
        KingdomMaps.sendTo(player, kingdom);
        player.connection.send(new ClientboundCustomPayloadPacket(view(player, kingdom)));
    }

    /** A button in the Town Hall screen. Every action re-checks the player's rank. */
    public static void handle(ServerPlayer player, KingdomActionPayload action) {
        Optional<Kingdom> before = Kingdoms.ofPlayer(player);
        if (before.isEmpty()) {
            return;
        }
        Kingdom kingdom = before.get();
        UUID target = action.target().orElse(null);
        switch (action.action()) {
            case PROMOTE -> {
                if (target != null) Kingdoms.promote(player, target);
            }
            case DEMOTE -> {
                if (target != null) Kingdoms.demote(player, target);
            }
            case KICK -> {
                if (target != null) Kingdoms.kick(player, target);
            }
            case BAN -> {
                if (target != null) Kingdoms.ban(player, target);
            }
            case UNBAN -> {
                if (target != null) Kingdoms.unban(player, target);
            }
            case INVITE -> {
                ServerPlayer invited = player.server.getPlayerList().getPlayerByName(action.text().strip());
                if (invited != null) {
                    Kingdoms.invite(player, invited);
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("kingdoms.kingdom.invite.not_online", action.text()));
                }
            }
            case SET_COLOR -> {
                if (action.value() >= 0 && action.value() < DyeColor.values().length) {
                    Kingdoms.setColor(player, DyeColor.byId(action.value()));
                }
            }
            case SET_BANNER -> {
                int slot = action.value();
                if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.getItem() instanceof BannerItem) {
                        Kingdoms.setBanner(player, stack);
                    }
                }
            }
            case SET_INVITE_ONLY -> Kingdoms.setSettings(player,
                    new Kingdom.Settings(action.value() != 0, kingdom.settings().friendlyFire()));
            case SET_FRIENDLY_FIRE -> Kingdoms.setSettings(player,
                    new Kingdom.Settings(kingdom.settings().inviteOnly(), action.value() != 0));
            case LEVEL_UP -> Kingdoms.levelUp(player);
            case UNLOCK_PERK -> KingdomPerks.upgrade(player, action.text());
            case COLLECT_TAXES -> TaxCollector.collect(player);
            case LEAVE -> {
                Kingdoms.leave(player);
                return;
            }
        }
        Kingdoms.ofPlayer(player).ifPresent(current -> player.connection.send(new ClientboundCustomPayloadPacket(view(player, current))));
    }
}
