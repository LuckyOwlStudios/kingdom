package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * {@code /kingdom diplomacy}: lists the kingdom's allies, enemies and pending requests, and
 * {@code ally | cancel | accept | decline | break | enemy | peace <kingdom>} change them. The chat buttons
 * on an alliance request run {@code accept} and {@code decline}.
 */
final class DiplomacyCommand {

    /** Every kingdom but the player's own. */
    private static final SuggestionProvider<CommandSourceStack> OTHER_KINGDOMS = (context, builder) -> {
        Optional<UUID> own = Optional.ofNullable(context.getSource().getPlayer()).flatMap(Kingdoms::ofPlayer).map(Kingdom::id);
        return SharedSuggestionProvider.suggest(KingdomData.get(context.getSource().getServer()).all().stream()
                .filter(kingdom -> own.map(id -> !id.equals(kingdom.id())).orElse(true))
                .map(Kingdom::name), builder);
    };

    private DiplomacyCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("diplomacy")
                .executes(DiplomacyCommand::list)
                .then(action("ally", Diplomacy::requestAlliance))
                .then(action("cancel", Diplomacy::cancelRequest))
                .then(action("accept", Diplomacy::accept))
                .then(action("decline", Diplomacy::decline))
                .then(action("break", Diplomacy::endAlliance))
                .then(action("enemy", Diplomacy::declareEnemy))
                .then(action("peace", Diplomacy::makePeace));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, BiPredicate<ServerPlayer, Kingdom> action) {
        return Commands.literal(name).then(Commands.argument("kingdom", StringArgumentType.greedyString()).suggests(OTHER_KINGDOMS)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String kingdomName = StringArgumentType.getString(context, "kingdom");
                    Optional<Kingdom> target = KingdomData.get(player.server).byName(kingdomName);
                    if (target.isEmpty()) {
                        context.getSource().sendFailure(Component.translatable("kingdoms.kingdom.unknown", kingdomName));
                        return 0;
                    }
                    return action.test(player, target.get()) ? 1 : 0;
                }));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Kingdom> own = Kingdoms.ofPlayer(player);
        if (own.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("kingdoms.kingdom.not_in_kingdom"));
            return 0;
        }
        Kingdom us = own.get();
        KingdomData data = KingdomData.get(player.server);
        KingdomDiplomacy diplomacy = KingdomDiplomacy.get(player.server);
        Collection<Kingdom> others = data.all().stream().filter(other -> other != us).toList();
        line(context, "kingdoms.diplomacy.list.allies", others.stream().filter(other -> diplomacy.areAllied(us.id(), other.id())).toList());
        line(context, "kingdoms.diplomacy.list.enemies", others.stream().filter(other -> diplomacy.hasDeclaredEnemy(us.id(), other.id())).toList());
        line(context, "kingdoms.diplomacy.list.hostile", others.stream().filter(other -> diplomacy.hasDeclaredEnemy(other.id(), us.id())).toList());
        line(context, "kingdoms.diplomacy.list.incoming", others.stream().filter(other -> diplomacy.hasRequested(other.id(), us.id())).toList());
        line(context, "kingdoms.diplomacy.list.outgoing", others.stream().filter(other -> diplomacy.hasRequested(us.id(), other.id())).toList());
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> context, String key, Collection<Kingdom> kingdoms) {
        Component names = kingdoms.isEmpty()
                ? Component.translatable("kingdoms.diplomacy.list.none")
                : ComponentUtils.formatList(kingdoms, Kingdom::displayName);
        context.getSource().sendSuccess(() -> Component.translatable(key, names), false);
    }
}
