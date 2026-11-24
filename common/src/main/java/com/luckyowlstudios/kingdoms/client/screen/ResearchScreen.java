package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerk;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerks;
import com.luckyowlstudios.kingdoms.network.ResearchActionPayload;
import com.luckyowlstudios.kingdoms.network.ResearchViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Scholar's Desk's Research screen: the kingdom perk tree. The leader picks the next tier of a perk to research;
 * its points are spent and the kingdom's Scholars get to work on it. The bar at the top shows how far along it is.
 */
public class ResearchScreen extends Screen {

    private static final int NODE_SIZE = 22;
    private static final int COLUMN_GAP = 36;
    private static final int ROW_GAP = 32;
    /** Room on the left of the tree for the branch names. */
    private static final int LABEL_WIDTH = 72;
    private static final int WIDTH = 360;
    private static final int BAR_WIDTH = 240;
    private static final int BAR_HEIGHT = 5;

    private ResearchViewPayload view;
    private final List<PerkNode> nodes = new ArrayList<>();
    private Button cancel;

    public ResearchScreen(ResearchViewPayload view) {
        super(Component.translatable("kingdoms.research.screen.title", view.name()));
        this.view = view;
    }

    /** Opens the screen, or refreshes it in place if it's already open. */
    public static void show(ResearchViewPayload view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ResearchScreen screen) {
            screen.view = view;
            screen.rebuildWidgets();
        } else {
            minecraft.setScreen(new ResearchScreen(view));
        }
    }

    private boolean canResearch() {
        return this.view.viewerRank().atLeast(KingdomPerks.UNLOCK_RANK);
    }

    private void send(ResearchActionPayload.Action action, String perk) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new ResearchActionPayload(action, this.view.desk(), perk)));
        }
    }

    private int left() {
        return this.width / 2 - WIDTH / 2;
    }

    private int top() {
        return Math.max(8, (this.height - 36 - 200) / 2);
    }

    private int treeTop() {
        return this.top() + 64;
    }

    @Override
    protected void init() {
        this.nodes.clear();
        int treeLeft = this.left() + LABEL_WIDTH;
        for (KingdomPerk perk : KingdomPerks.all()) {
            PerkNode node = this.addRenderableWidget(new PerkNode(perk));
            node.setPosition(treeLeft + perk.column() * COLUMN_GAP, this.treeTop() + perk.row() * ROW_GAP);
            this.nodes.add(node);
        }
        this.cancel = this.addRenderableWidget(Button.builder(Component.translatable("kingdoms.research.screen.cancel"),
                button -> this.send(ResearchActionPayload.Action.CANCEL, "")).bounds(this.left() + WIDTH - 80, this.top() + 34, 80, 20).build());
        this.cancel.visible = this.view.research().isPresent();
        this.cancel.active = this.canResearch();
        this.cancel.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(this.canResearch()
                ? "kingdoms.research.screen.cancel.tooltip" : "kingdoms.kingdom.screen.perks.leader_only")));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = this.left();
        int top = this.top();
        graphics.drawCenteredString(this.font, Component.translatable("kingdoms.research.screen.title",
                Component.literal(this.view.name()).withStyle(style -> style.withColor(this.view.color().getTextColor()))), this.width / 2, top, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("kingdoms.kingdom.screen.perk_points",
                this.view.perkPoints(), KingdomPerks.POINTS_PER_LEVEL), left, top + 16, 0xFFD700);
        Component scholars = Component.translatable("kingdoms.research.screen.scholars", this.view.scholars());
        graphics.drawString(this.font, scholars, left + WIDTH - this.font.width(scholars), top + 16, 0xA0A0A0);
        this.renderProgress(graphics, left, top + 34);

        for (int row = 0; row < KingdomPerks.BRANCHES.size(); row++) {
            graphics.drawString(this.font, Component.translatable("kingdoms.perk.branch." + KingdomPerks.BRANCHES.get(row)),
                    left, this.treeTop() + row * ROW_GAP + (NODE_SIZE - 8) / 2, 0xA0A0A0);
        }
        if (!this.canResearch()) {
            graphics.drawCenteredString(this.font, Component.translatable("kingdoms.kingdom.screen.perks.leader_only"),
                    this.width / 2, this.treeTop() + KingdomPerks.BRANCHES.size() * ROW_GAP + 2, 0xFF8080);
        }

        // Lines from each perk's prerequisites, gold once the prerequisite is unlocked.
        for (PerkNode node : this.nodes) {
            for (ResourceLocation id : node.perk.prerequisites()) {
                this.nodes.stream().filter(other -> other.perk.id().equals(id)).findFirst().ifPresent(from -> {
                    int color = this.view.perkTier(id) > 0 ? 0xFFFFD700 : 0xFF606060;
                    int x1 = from.getX() + NODE_SIZE;
                    int y1 = from.getY() + NODE_SIZE / 2;
                    int x2 = node.getX();
                    int y2 = node.getY() + NODE_SIZE / 2;
                    int mid = (x1 + x2) / 2;
                    graphics.hLine(x1, mid, y1, color);
                    graphics.vLine(mid, Math.min(y1, y2), Math.max(y1, y2), color);
                    graphics.hLine(mid, x2, y2, color);
                });
            }
        }

        for (PerkNode node : this.nodes) {
            if (node.isHovered()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                node.tooltip().forEach(line -> lines.addAll(this.font.split(line, 220)));
                graphics.renderTooltip(this.font, lines, mouseX, mouseY);
            }
        }
    }

    /** What's being researched, a bar with how far along it is, and how long is left at the current pace. */
    private void renderProgress(GuiGraphics graphics, int left, int top) {
        Optional<ResearchViewPayload.Progress> research = this.view.research();
        if (research.isEmpty()) {
            graphics.drawString(this.font, Component.translatable(this.canResearch()
                    ? "kingdoms.research.screen.idle" : "kingdoms.research.screen.idle.member"), left, top + 6, 0xA0A0A0);
            return;
        }
        ResearchViewPayload.Progress progress = research.get();
        Component perk = KingdomPerks.byId(progress.perk()).map(KingdomPerk::displayName).orElse(Component.literal(progress.perk().toString()));
        graphics.drawString(this.font, Component.translatable("kingdoms.research.screen.researching", perk, progress.tier()), left, top, 0xFFFFFF);
        int barTop = top + 12;
        graphics.fill(left, barTop, left + BAR_WIDTH, barTop + BAR_HEIGHT, 0xFF303030);
        int filled = progress.duration() <= 0 ? BAR_WIDTH : (int) ((long) BAR_WIDTH * Math.min(progress.progress(), progress.duration()) / progress.duration());
        graphics.fill(left, barTop, left + filled, barTop + BAR_HEIGHT, 0xFF55AAFF);
        int percent = progress.duration() <= 0 ? 100 : (int) (100L * progress.progress() / progress.duration());
        Component pace;
        if (this.view.scholars() > 0) {
            int seconds = (progress.duration() - progress.progress() + this.view.scholars() - 1) / this.view.scholars();
            pace = Component.translatable("kingdoms.research.screen.time_left", percent, seconds / 60, String.format("%02d", seconds % 60));
        } else {
            pace = Component.translatable("kingdoms.research.screen.paused", percent).withStyle(ChatFormatting.RED);
        }
        graphics.drawString(this.font, pace, left, barTop + BAR_HEIGHT + 3, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Closed: the server can stop sending updates. */
    @Override
    public void removed() {
        super.removed();
        this.send(ResearchActionPayload.Action.CLOSE, "");
    }

    /** One perk in the tree: its icon, framed by how far along it is, with a pip per tier underneath. */
    private final class PerkNode extends AbstractButton {

        private final KingdomPerk perk;

        PerkNode(KingdomPerk perk) {
            super(0, 0, NODE_SIZE, NODE_SIZE, perk.displayName());
            this.perk = perk;
            this.active = ResearchScreen.this.canResearch() && this.blocker() == KingdomPerks.Blocker.NONE;
        }

        private int tier() {
            return ResearchScreen.this.view.perkTier(this.perk.id());
        }

        private boolean isResearching() {
            return this.perk.id().equals(ResearchScreen.this.view.researching());
        }

        private KingdomPerks.Blocker blocker() {
            ResearchViewPayload view = ResearchScreen.this.view;
            return KingdomPerks.blocker(this.perk, view.level(), view.perkPoints(), view::perkTier, view.researching());
        }

        @Override
        public void onPress() {
            ResearchScreen.this.send(ResearchActionPayload.Action.START, this.perk.id().toString());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int tier = this.tier();
            int frame = this.isResearching() ? 0xFF55AAFF : tier >= this.perk.maxTier() ? 0xFFFFD700 : tier > 0 ? 0xFFC0A040
                    : this.active ? 0xFF55FF55 : 0xFF404040;
            graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, frame);
            graphics.fill(x + 2, y + 2, x + NODE_SIZE - 2, y + NODE_SIZE - 2,
                    this.isHoveredOrFocused() ? 0xFF9A9A9A : 0xFF6B6B6B);
            graphics.renderItem(new ItemStack(this.perk.icon()), x + 3, y + 3);
            if (tier == 0) {
                // Not unlocked yet: dim the icon.
                graphics.fill(x + 2, y + 2, x + NODE_SIZE - 2, y + NODE_SIZE - 2, 0x90000000);
            }
            // One pip per tier: gold for the ones unlocked, blue for the one being researched.
            int pipLeft = x + NODE_SIZE / 2 - (this.perk.maxTier() * 5 - 1) / 2;
            for (int n = 1; n <= this.perk.maxTier(); n++) {
                int pipX = pipLeft + (n - 1) * 5;
                int color = n <= tier ? 0xFFFFD700 : n == tier + 1 && this.isResearching() ? 0xFF55AAFF : 0xFF404040;
                graphics.fill(pipX, y + NODE_SIZE + 2, pipX + 4, y + NODE_SIZE + 6, color);
            }
        }

        List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            int tier = this.tier();
            lines.add(this.perk.displayName().copy().withStyle(ChatFormatting.GOLD));
            lines.add(this.perk.description().copy().withStyle(ChatFormatting.GRAY));
            for (int n = 1; n <= this.perk.maxTier(); n++) {
                KingdomPerk.Tier info = this.perk.tier(n);
                ChatFormatting color = n <= tier ? ChatFormatting.GREEN : n == tier + 1 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
                lines.add(Component.translatable("kingdoms.research.screen.perk_tier", n, this.perk.tierDescription(n),
                        info.requiredLevel(), info.cost(), info.researchSeconds() / 60).withStyle(color));
            }
            if (!this.perk.prerequisites().isEmpty()) {
                Component names = ComponentUtils.formatList(this.perk.prerequisites().stream()
                        .map(id -> KingdomPerks.byId(id).map(KingdomPerk::displayName).orElse(Component.literal(id.toString())))
                        .toList(), Component.literal(", "));
                lines.add(Component.translatable("kingdoms.kingdom.screen.perk_requires", names).withStyle(ChatFormatting.GRAY));
            }
            KingdomPerks.Blocker blocker = this.blocker();
            Component status;
            if (blocker == KingdomPerks.Blocker.NONE) {
                status = ResearchScreen.this.canResearch()
                        ? Component.translatable(tier == 0 ? "kingdoms.research.screen.click_unlock" : "kingdoms.research.screen.click_upgrade")
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("kingdoms.kingdom.screen.perks.leader_only").withStyle(ChatFormatting.RED);
            } else {
                KingdomPerk.Tier next = this.perk.tier(Math.min(tier + 1, this.perk.maxTier()));
                ChatFormatting color = blocker == KingdomPerks.Blocker.MAXED ? ChatFormatting.GOLD
                        : blocker == KingdomPerks.Blocker.RESEARCHING ? ChatFormatting.AQUA : ChatFormatting.RED;
                status = Component.translatable(blocker.translationKey() + ".short", next.requiredLevel(), next.cost()).withStyle(color);
            }
            lines.add(status);
            return lines;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
