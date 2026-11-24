package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;

/**
 * {@code /kingdom} for every player: info, list, invite, join, kick, leave and banner.
 * Founding one happens at a Town Hall.
 */
public final class KingdomCommand {

    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomData.get(context.getSource().getServer()).all().stream().map(Kingdom::name), builder);

    private KingdomCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdom")
                .executes(context -> info(context.getSource(), Kingdoms.ofPlayer(context.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOMS)
                                .executes(context -> info(context.getSource(),
                                        KingdomData.get(context.getSource().getServer()).byName(StringArgumentType.getString(context, "kingdom"))))))
                .then(Commands.literal("list").executes(KingdomCommand::list))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> result(Kingdoms.invite(context.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("join")
                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOMS)
                                .executes(context -> result(Kingdoms.join(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "kingdom"))))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(KingdomCommand::kick)))
                .then(Commands.literal("leave")
                        .executes(context -> result(Kingdoms.leave(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("home")
                        .executes(context -> result(Homecoming.goHome(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("vault")
                        .executes(context -> result(SharedVault.open(context.getSource().getPlayerOrException()))))
                // Operators: give a kingdom XP or set its level outright, for testing or running a server.
                .then(Commands.literal("addxp").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> addXp(context.getSource(), IntegerArgumentType.getInteger(context, "amount"),
                                        Kingdoms.ofPlayer(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOMS)
                                        .executes(context -> addXp(context.getSource(), IntegerArgumentType.getInteger(context, "amount"),
                                                KingdomData.get(context.getSource().getServer()).byName(StringArgumentType.getString(context, "kingdom")))))))
                .then(Commands.literal("setlevel").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, KingdomLevel.MAX_LEVEL))
                                .executes(context -> setLevel(context.getSource(), IntegerArgumentType.getInteger(context, "level"),
                                        Kingdoms.ofPlayer(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOMS)
                                        .executes(context -> setLevel(context.getSource(), IntegerArgumentType.getInteger(context, "level"),
                                                KingdomData.get(context.getSource().getServer()).byName(StringArgumentType.getString(context, "kingdom")))))))
                // Operators: finish a kingdom's research straight away.
                .then(Commands.literal("finishresearch").requires(source -> source.hasPermission(2))
                        .executes(context -> finishResearch(context.getSource(), Kingdoms.ofPlayer(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(KINGDOMS)
                                .executes(context -> finishResearch(context.getSource(),
                                        KingdomData.get(context.getSource().getServer()).byName(StringArgumentType.getString(context, "kingdom"))))))
                .then(DiplomacyCommand.build())
                .then(Commands.literal("banner")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return result(Kingdoms.setBanner(player, player.getMainHandItem()));
                        })));
    }

    private static int setLevel(CommandSourceStack source, int level, Optional<Kingdom> kingdom) {
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("kingdoms.kingdom.not_found"));
            return 0;
        }
        Kingdoms.setLevel(source.getServer(), kingdom.get(), level);
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.level.set", kingdom.get().displayName(), level), true);
        return 1;
    }

    private static int finishResearch(CommandSourceStack source, Optional<Kingdom> kingdom) {
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("kingdoms.kingdom.not_found"));
            return 0;
        }
        if (!KingdomResearch.finish(source.getServer(), kingdom.get())) {
            source.sendFailure(Component.translatable("kingdoms.research.command.nothing", kingdom.get().displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("kingdoms.research.command.finished", kingdom.get().displayName()), true);
        return 1;
    }

    private static int addXp(CommandSourceStack source, int amount, Optional<Kingdom> kingdom) {
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("kingdoms.kingdom.not_found"));
            return 0;
        }
        KingdomXp.add(source.getServer(), kingdom.get(), amount);
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.xp.added", amount, kingdom.get().displayName()), true);
        return 1;
    }

    private static int result(boolean success) {
        return success ? 1 : 0;
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer leader = context.getSource().getPlayerOrException();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        int kicked = 0;
        for (GameProfile profile : profiles) {
            kicked += result(Kingdoms.kick(leader, profile.getId()));
        }
        return kicked;
    }

    private static int info(CommandSourceStack source, Optional<Kingdom> kingdom) {
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("kingdoms.kingdom.not_found"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        Kingdom k = kingdom.get();
        String leader = KingdomTeams.playerName(server, k.leader()).orElse("?");
        Component members = ComponentUtils.formatList(k.members(),
                member -> Component.literal(KingdomTeams.playerName(server, member).orElse("?")));
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.title", k.displayName()), false);
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.leader", leader), false);
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.members", k.members().size(), members), false);
        source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.level", k.level(),
                k.residents().size(), KingdomHouses.homes(server, k), k.claims().size(), k.landCap()), false);
        k.townHall().ifPresentOrElse(
                hall -> source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.town_hall",
                        hall.pos().getX(), hall.pos().getY(), hall.pos().getZ()), false),
                () -> source.sendSuccess(() -> Component.translatable("kingdoms.kingdom.info.no_town_hall"), false));
        source.sendSuccess(() -> k.banner().isPresent()
                ? Component.translatable("kingdoms.kingdom.info.banner", k.banner().get().getDisplayName())
                : Component.translatable("kingdoms.kingdom.info.no_banner"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Collection<Kingdom> all = KingdomData.get(context.getSource().getServer()).all();
        if (all.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("kingdoms.kingdom.list.none"), false);
            return 0;
        }
        Component names = ComponentUtils.formatList(all, Kingdom::displayName);
        context.getSource().sendSuccess(() -> Component.translatable("kingdoms.kingdom.list", all.size(), names), false);
        return all.size();
    }
}
