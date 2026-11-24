package com.luckyowlstudios.kingdoms.kingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Optional;

/**
 * The Shared Vault perk: a chest the whole kingdom shares. Every member can open it.
 * <ul>
 *     <li>Tier 1: three rows, opened from the Town Hall.</li>
 *     <li>Tier 2: six rows.</li>
 *     <li>Tier 3: open it from anywhere with {@code /kingdom vault}.</li>
 * </ul>
 * Items in rows a lower tier can't show (after an operator lowers the level) are kept until the rows come back.
 */
public final class SharedVault {

    /** Slots saved for every kingdom: the six rows of the top tier. */
    public static final int SIZE = 54;
    /** How close to the Town Hall a member has to be below tier 3. */
    private static final double TOWN_HALL_RANGE_SQR = 8.0 * 8.0;

    private SharedVault() {
    }

    public static int rows(int tier) {
        return tier <= 0 ? 0 : tier == 1 ? 3 : 6;
    }

    /** Opens the vault for a member, if the perk allows it from where they are. */
    public static boolean open(ServerPlayer player) {
        Optional<Kingdom> found = Kingdoms.ofPlayer(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdoms.kingdom.not_in_kingdom"));
            return false;
        }
        Kingdom kingdom = found.get();
        int tier = KingdomPerks.activeTier(kingdom, KingdomPerks.SHARED_VAULT);
        if (tier == 0) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.not_unlocked", KingdomPerks.SHARED_VAULT.displayName()));
            return false;
        }
        boolean atHall = kingdom.townHall().filter(hall -> hall.dimension().equals(player.level().dimension())
                && player.distanceToSqr(hall.pos().getCenter()) <= TOWN_HALL_RANGE_SQR).isPresent();
        if (tier < 3 && !atHall) {
            player.sendSystemMessage(Component.translatable("kingdoms.perk.shared_vault.at_town_hall"));
            return false;
        }
        int rows = rows(tier);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> rows == 3
                ? new ChestMenu(MenuType.GENERIC_9x3, id, inventory, kingdom.vault(), 3)
                : ChestMenu.sixRows(id, inventory, kingdom.vault()),
                Component.translatable("kingdoms.perk.shared_vault.title", kingdom.displayName())));
        return true;
    }
}
