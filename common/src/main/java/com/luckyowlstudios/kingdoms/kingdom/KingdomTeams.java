package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps one vanilla scoreboard team per kingdom, so members' names show in the kingdom's colour and the
 * usual team rules (no friendly fire, seeing invisible teammates) apply.
 */
public final class KingdomTeams {

    private KingdomTeams() {
    }

    public static String teamName(Kingdom kingdom) {
        return "kingdoms." + kingdom.id().toString().substring(0, 8);
    }

    static void sync(MinecraftServer server, Kingdom kingdom) {
        ServerScoreboard scoreboard = server.getScoreboard();
        String name = teamName(kingdom);
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            team.setSeeFriendlyInvisibles(true);
        }
        team.setDisplayName(Component.literal(kingdom.name()));
        team.setColor(chatColor(kingdom.color()));
        team.setAllowFriendlyFire(kingdom.settings().friendlyFire());
        for (UUID member : kingdom.members()) {
            PlayerTeam finalTeam = team;
            playerName(server, member).ifPresent(playerName -> {
                if (finalTeam != scoreboard.getPlayersTeam(playerName)) {
                    scoreboard.addPlayerToTeam(playerName, finalTeam);
                }
            });
        }
    }

    static void removePlayer(MinecraftServer server, Kingdom kingdom, UUID player) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName(kingdom));
        playerName(server, player).ifPresent(playerName -> {
            if (team != null && scoreboard.getPlayersTeam(playerName) == team) {
                scoreboard.removePlayerFromTeam(playerName, team);
            }
        });
    }

    static void remove(MinecraftServer server, Kingdom kingdom) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName(kingdom));
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    static Optional<String> playerName(MinecraftServer server, UUID player) {
        var online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return Optional.of(online.getScoreboardName());
        }
        return server.getProfileCache() == null ? Optional.empty()
                : server.getProfileCache().get(player).map(profile -> profile.getName());
    }

    /** The closest chat colour to a dye colour, for the team. */
    static ChatFormatting chatColor(DyeColor color) {
        return switch (color) {
            case WHITE -> ChatFormatting.WHITE;
            case ORANGE, BROWN -> ChatFormatting.GOLD;
            case MAGENTA, PINK -> ChatFormatting.LIGHT_PURPLE;
            case LIGHT_BLUE -> ChatFormatting.AQUA;
            case YELLOW -> ChatFormatting.YELLOW;
            case LIME -> ChatFormatting.GREEN;
            case GRAY -> ChatFormatting.DARK_GRAY;
            case LIGHT_GRAY -> ChatFormatting.GRAY;
            case CYAN -> ChatFormatting.DARK_AQUA;
            case PURPLE -> ChatFormatting.DARK_PURPLE;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.DARK_GREEN;
            case RED -> ChatFormatting.RED;
            case BLACK -> ChatFormatting.BLACK;
        };
    }
}
