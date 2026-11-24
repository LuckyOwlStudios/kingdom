package com.luckyowlstudios.kingdoms.client;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The toast shown when the kingdom unlocks or upgrades a perk: the perk's icon and name, like an advancement. */
public class PerkToast implements Toast {

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME = 5000L;

    private final KingdomPerk perk;
    private final int tier;

    public PerkToast(KingdomPerk perk, int tier) {
        this.perk = perk;
        this.tier = tier;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceLastVisible) {
        graphics.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        Component title = this.tier <= 1
                ? Component.translatable("kingdoms.perk.toast.unlocked")
                : Component.translatable("kingdoms.perk.toast.upgraded", this.tier);
        graphics.drawString(toasts.getMinecraft().font, title, 30, 7, 0xFFFF00, false);
        graphics.drawString(toasts.getMinecraft().font, this.perk.displayName(), 30, 18, 0xFFFFFF, false);
        graphics.renderFakeItem(new ItemStack(this.perk.icon()), 8, 8);
        return timeSinceLastVisible >= DISPLAY_TIME * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
