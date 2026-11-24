package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.kingdom.KingdomPerk;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerks;
import com.luckyowlstudios.kingdoms.network.ResearchActionPayload;
import com.luckyowlstudios.kingdoms.network.ResearchViewPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Scholar's Desk's Research screen: the kingdom perk tree, drawn like the vanilla Advancements screen. A framed
 * window with a tiled background holds the tree, which can be dragged around; each perk is an advancement frame, and
 * hovering one shows its name on a banner (gold for the share of its tiers unlocked) with its details underneath.
 * <p>
 * The leader clicks a perk to research its next tier; its points are spent and the kingdom's Scholars get to work on
 * it. Under the window is what's being researched and how far along it is.
 * <p>
 * Only vanilla advancement textures are used, so resource packs that restyle advancements restyle this too.
 */
public class ResearchScreen extends Screen {

    // ---- Vanilla advancement textures ----
    private static final ResourceLocation WINDOW = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    /** Tiled behind the tree. Any 16x16 texture works; this is the Adventure tab's. */
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/adventure.png");
    private static final ResourceLocation TITLE_BOX = ResourceLocation.withDefaultNamespace("advancements/title_box");
    private static final ResourceLocation BOX_OBTAINED = ResourceLocation.withDefaultNamespace("advancements/box_obtained");
    private static final ResourceLocation BOX_UNOBTAINED = ResourceLocation.withDefaultNamespace("advancements/box_unobtained");
    private static final ResourceLocation FRAME_LOCKED = ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
    private static final ResourceLocation FRAME_UNLOCKED = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
    /** A perk with every tier unlocked gets the fancier challenge frame. */
    private static final ResourceLocation FRAME_MAXED = ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained");

    // ---- Window layout (matches vanilla's window.png) ----
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int INSIDE_X = 9;
    private static final int INSIDE_Y = 18;
    private static final int INSIDE_WIDTH = 234;
    private static final int INSIDE_HEIGHT = 113;

    // ---- Tree layout, in tree space (before panning) ----
    private static final int FRAME_SIZE = 26;
    private static final int COLUMN_GAP = 34;
    private static final int ROW_GAP = 34;
    private static final int PADDING = 8;
    /** Room under each frame for its tier pips. */
    private static final int PIP_ROOM = 7;

    // ---- Hover banner ----
    private static final int BANNER_HEIGHT = 26;
    /** The banner sprites are this wide; a banner can't be wider. */
    private static final int BANNER_MAX_WIDTH = 200;
    private static final int BANNER_MIN_WIDTH = 150;

    private static final int BAR_HEIGHT = 5;
    private static final int TEXT_DARK = 0x404040;

    private ResearchViewPayload view;
    private Button cancel;

    /** Where the tree has been dragged to; 0, 0 shows its top left corner. */
    private double scrollX;
    private double scrollY;
    /** Set once a press on the tree turns into a drag, so letting go doesn't also click a perk. */
    private boolean dragged;
    private boolean pressedInside;

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

    // ---- Layout ----

    private int windowLeft() {
        return (this.width - WINDOW_WIDTH) / 2;
    }

    /** Leaves room for the title above, and the research progress and Done button below. */
    private int windowTop() {
        return Math.max(18, (this.height - WINDOW_HEIGHT - 76) / 2 + 6);
    }

    private int insideLeft() {
        return this.windowLeft() + INSIDE_X;
    }

    private int insideTop() {
        return this.windowTop() + INSIDE_Y;
    }

    private int labelWidth() {
        int widest = 0;
        for (String branch : KingdomPerks.BRANCHES) {
            widest = Math.max(widest, this.font.width(branchName(branch)));
        }
        return widest + 10;
    }

    private static Component branchName(String branch) {
        return Component.translatable("kingdoms.perk.branch." + branch);
    }

    private int treeWidth() {
        int columns = KingdomPerks.all().stream().mapToInt(KingdomPerk::column).max().orElse(0);
        return PADDING + this.labelWidth() + columns * COLUMN_GAP + FRAME_SIZE + PADDING;
    }

    private int treeHeight() {
        int rows = KingdomPerks.all().stream().mapToInt(KingdomPerk::row).max().orElse(0);
        return PADDING + rows * ROW_GAP + FRAME_SIZE + PIP_ROOM + PADDING;
    }

    /** Where the tree's top left corner is on screen, after panning (or centred, if it fits). */
    private int treeLeft() {
        int spare = INSIDE_WIDTH - this.treeWidth();
        return this.insideLeft() + (spare >= 0 ? spare / 2 : Mth.floor(this.scrollX));
    }

    private int treeTop() {
        int spare = INSIDE_HEIGHT - this.treeHeight();
        return this.insideTop() + (spare >= 0 ? spare / 2 : Mth.floor(this.scrollY));
    }

    private int frameX(KingdomPerk perk) {
        return this.treeLeft() + PADDING + this.labelWidth() + perk.column() * COLUMN_GAP;
    }

    private int frameY(KingdomPerk perk) {
        return this.treeTop() + PADDING + perk.row() * ROW_GAP;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.insideLeft() && mouseX < this.insideLeft() + INSIDE_WIDTH
                && mouseY >= this.insideTop() && mouseY < this.insideTop() + INSIDE_HEIGHT;
    }

    private Optional<KingdomPerk> perkAt(double mouseX, double mouseY) {
        if (!this.isInside(mouseX, mouseY)) {
            return Optional.empty();
        }
        for (KingdomPerk perk : KingdomPerks.all()) {
            int x = this.frameX(perk);
            int y = this.frameY(perk);
            if (mouseX >= x && mouseX < x + FRAME_SIZE && mouseY >= y && mouseY < y + FRAME_SIZE) {
                return Optional.of(perk);
            }
        }
        return Optional.empty();
    }

    @Override
    protected void init() {
        int progressTop = this.windowTop() + WINDOW_HEIGHT + 4;
        this.cancel = this.addRenderableWidget(Button.builder(Component.translatable("kingdoms.research.screen.cancel"),
                button -> this.send(ResearchActionPayload.Action.CANCEL, ""))
                .bounds(this.windowLeft() + WINDOW_WIDTH - 70, progressTop + 2, 70, 20).build());
        this.cancel.visible = this.view.research().isPresent();
        this.cancel.active = this.canResearch();
        this.cancel.setTooltip(Tooltip.create(Component.translatable(this.canResearch()
                ? "kingdoms.research.screen.cancel.tooltip" : "kingdoms.kingdom.screen.perks.leader_only")));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    // ---- Rendering ----

    /** Vanilla's menu blur plus a dark tint, so the kingdom UI stands out from the world behind it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = this.windowLeft();
        int top = this.windowTop();

        graphics.drawCenteredString(this.font, Component.translatable("kingdoms.research.screen.title",
                Component.literal(this.view.name()).withStyle(style -> style.withColor(this.view.color().getTextColor()))),
                this.width / 2, top - 12, 0xFFFFFF);

        this.renderInside(graphics);

        RenderSystem.enableBlend();
        graphics.blit(WINDOW, left, top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        // The title bar: points to spend on the left, Scholars at work on the right.
        graphics.drawString(this.font, Component.translatable("kingdoms.kingdom.screen.perk_points.short", this.view.perkPoints()),
                left + 8, top + 6, TEXT_DARK, false);
        Component scholars = Component.translatable("kingdoms.research.screen.scholars", this.view.scholars());
        graphics.drawString(this.font, scholars, left + WINDOW_WIDTH - 8 - this.font.width(scholars), top + 6, TEXT_DARK, false);

        this.renderProgress(graphics, left, top + WINDOW_HEIGHT + 4);

        Optional<KingdomPerk> hovered = this.perkAt(mouseX, mouseY);
        hovered.ifPresent(perk -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            this.renderHover(graphics, perk);
            graphics.pose().popPose();
        });
    }

    /** The tiled background, branch names, the lines between perks, and the perks themselves, clipped to the window. */
    private void renderInside(GuiGraphics graphics) {
        int insideLeft = this.insideLeft();
        int insideTop = this.insideTop();
        graphics.enableScissor(insideLeft, insideTop, insideLeft + INSIDE_WIDTH, insideTop + INSIDE_HEIGHT);

        // Tile the background so it moves with the tree.
        int treeLeft = this.treeLeft();
        int treeTop = this.treeTop();
        int startX = insideLeft - Math.floorMod(insideLeft - treeLeft, 16);
        int startY = insideTop - Math.floorMod(insideTop - treeTop, 16);
        for (int x = startX; x < insideLeft + INSIDE_WIDTH; x += 16) {
            for (int y = startY; y < insideTop + INSIDE_HEIGHT; y += 16) {
                graphics.blit(BACKGROUND, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }

        for (int row = 0; row < KingdomPerks.BRANCHES.size(); row++) {
            graphics.drawString(this.font, branchName(KingdomPerks.BRANCHES.get(row)),
                    treeLeft + PADDING, treeTop + PADDING + row * ROW_GAP + (FRAME_SIZE - 8) / 2, TEXT_DARK, false);
        }

        // Lines from each perk's prerequisites: a dark outline first, then the core on top, like vanilla.
        this.renderLines(graphics, true);
        this.renderLines(graphics, false);

        RenderSystem.enableBlend();
        for (KingdomPerk perk : KingdomPerks.all()) {
            this.renderPerk(graphics, perk);
        }
        graphics.disableScissor();
    }

    private void renderLines(GuiGraphics graphics, boolean outline) {
        for (KingdomPerk perk : KingdomPerks.all()) {
            for (ResourceLocation id : perk.prerequisites()) {
                KingdomPerks.byId(id).ifPresent(from -> {
                    int color = outline ? 0xFF000000 : this.view.perkTier(id) > 0 ? 0xFFFFFFFF : 0xFF8B8B8B;
                    int x1 = this.frameX(from) + FRAME_SIZE;
                    int y1 = this.frameY(from) + FRAME_SIZE / 2;
                    int x2 = this.frameX(perk);
                    int y2 = this.frameY(perk) + FRAME_SIZE / 2;
                    int mid = (x1 + x2) / 2;
                    if (outline) {
                        graphics.fill(x1, y1 - 1, mid + 2, y1 + 2, color);
                        graphics.fill(mid - 1, Math.min(y1, y2) - 1, mid + 2, Math.max(y1, y2) + 2, color);
                        graphics.fill(mid - 1, y2 - 1, x2, y2 + 2, color);
                    } else {
                        graphics.hLine(x1, mid, y1, color);
                        graphics.vLine(mid, Math.min(y1, y2), Math.max(y1, y2), color);
                        graphics.hLine(mid, x2, y2, color);
                    }
                });
            }
        }
    }

    private int tier(KingdomPerk perk) {
        return this.view.perkTier(perk.id());
    }

    private boolean isResearching(KingdomPerk perk) {
        return perk.id().equals(this.view.researching());
    }

    private KingdomPerks.Blocker blocker(KingdomPerk perk) {
        return KingdomPerks.blocker(perk, this.view.level(), this.view.perkPoints(), this.view::perkTier, this.view.researching());
    }

    private boolean canStart(KingdomPerk perk) {
        return this.canResearch() && this.blocker(perk) == KingdomPerks.Blocker.NONE;
    }

    private ResourceLocation frameSprite(KingdomPerk perk) {
        int tier = this.tier(perk);
        return tier >= perk.maxTier() ? FRAME_MAXED : tier > 0 ? FRAME_UNLOCKED : FRAME_LOCKED;
    }

    /** One perk: its frame and icon, with a pip per tier underneath. */
    private void renderPerk(GuiGraphics graphics, KingdomPerk perk) {
        int x = this.frameX(perk);
        int y = this.frameY(perk);
        int tier = this.tier(perk);
        graphics.blitSprite(this.frameSprite(perk), x, y, FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(new ItemStack(perk.icon()), x + 5, y + 5);

        // Pips: gold for tiers unlocked, blue for the one being researched, green for one the leader can start now.
        int pipLeft = x + FRAME_SIZE / 2 - (perk.maxTier() * 5 - 1) / 2;
        int pipTop = y + FRAME_SIZE + 1;
        boolean researching = this.isResearching(perk);
        boolean startable = this.canStart(perk);
        for (int n = 1; n <= perk.maxTier(); n++) {
            int pipX = pipLeft + (n - 1) * 5;
            int color = n <= tier ? 0xFFFFD700
                    : n == tier + 1 && researching ? 0xFF55AAFF
                    : n == tier + 1 && startable ? 0xFF55FF55
                    : 0xFF505050;
            graphics.fill(pipX - 1, pipTop - 1, pipX + 5, pipTop + 5, 0xFF000000);
            graphics.fill(pipX, pipTop, pipX + 4, pipTop + 4, color);
        }
    }

    /**
     * The hover card, laid out like vanilla's: a banner with the frame, icon, name and tiers unlocked, gold for the
     * share of tiers unlocked and blue for the rest, and a panel with the details below it (or above, near the bottom
     * of the screen). It flips to the left of the perk near the right edge of the screen.
     */
    private void renderHover(GuiGraphics graphics, KingdomPerk perk) {
        int frameX = this.frameX(perk);
        int frameY = this.frameY(perk);
        int tier = this.tier(perk);
        Component title = perk.displayName();
        Component progress = Component.literal(tier + "/" + perk.maxTier());
        int progressWidth = this.font.width(progress);
        int width = Mth.clamp(29 + this.font.width(title) + 8 + progressWidth + 5, BANNER_MIN_WIDTH, BANNER_MAX_WIDTH);

        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : this.details(perk)) {
            lines.addAll(this.font.split(line, width - 10));
        }

        boolean flipLeft = frameX - 3 + width > this.width - 4;
        int boxX = flipLeft ? frameX + FRAME_SIZE + 3 - width : frameX - 3;
        int panelHeight = 32 + lines.size() * 9;
        boolean above = frameY + panelHeight > this.height - 4 && frameY + BANNER_HEIGHT - panelHeight >= 4;

        RenderSystem.enableBlend();
        int panelTop = above ? frameY + BANNER_HEIGHT - panelHeight : frameY;
        graphics.blitSprite(TITLE_BOX, boxX, panelTop, width, panelHeight);

        int gold = Mth.floor((float) tier / perk.maxTier() * width);
        int blue = width - gold;
        if (gold > 0) {
            graphics.blitSprite(BOX_OBTAINED, BANNER_MAX_WIDTH, BANNER_HEIGHT, 0, 0, boxX, frameY, gold, BANNER_HEIGHT);
        }
        if (blue > 0) {
            graphics.blitSprite(BOX_UNOBTAINED, BANNER_MAX_WIDTH, BANNER_HEIGHT, BANNER_MAX_WIDTH - blue, 0, boxX + gold, frameY, blue, BANNER_HEIGHT);
        }
        graphics.blitSprite(this.frameSprite(perk), frameX, frameY, FRAME_SIZE, FRAME_SIZE);
        graphics.renderFakeItem(new ItemStack(perk.icon()), frameX + 5, frameY + 5);

        if (flipLeft) {
            graphics.drawString(this.font, title, boxX + 5, frameY + 9, 0xFFFFFF);
            graphics.drawString(this.font, progress, frameX - progressWidth - 4, frameY + 9, 0xFFFFFF);
        } else {
            graphics.drawString(this.font, title, frameX + FRAME_SIZE + 3, frameY + 9, 0xFFFFFF);
            graphics.drawString(this.font, progress, boxX + width - progressWidth - 5, frameY + 9, 0xFFFFFF);
        }

        int textTop = above ? panelTop + 7 : frameY + BANNER_HEIGHT + 4;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), boxX + 5, textTop + i * 9, 0xAAAAAA, false);
        }
    }

    /** The text under a perk's banner: what it does, each tier, what it needs, and whether it can be researched. */
    private List<Component> details(KingdomPerk perk) {
        List<Component> lines = new ArrayList<>();
        int tier = this.tier(perk);
        lines.add(perk.description().copy().withStyle(ChatFormatting.GREEN));
        for (int n = 1; n <= perk.maxTier(); n++) {
            KingdomPerk.Tier info = perk.tier(n);
            ChatFormatting color = n <= tier ? ChatFormatting.GOLD : n == tier + 1 ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            lines.add(Component.translatable("kingdoms.research.screen.perk_tier", n, perk.tierDescription(n),
                    info.requiredLevel(), info.cost(), info.researchSeconds() / 60).withStyle(color));
        }
        if (!perk.prerequisites().isEmpty()) {
            Component names = ComponentUtils.formatList(perk.prerequisites().stream()
                    .map(id -> KingdomPerks.byId(id).map(KingdomPerk::displayName).orElse(Component.literal(id.toString())))
                    .toList(), Component.literal(", "));
            lines.add(Component.translatable("kingdoms.kingdom.screen.perk_requires", names).withStyle(ChatFormatting.GRAY));
        }
        KingdomPerks.Blocker blocker = this.blocker(perk);
        Component status;
        if (blocker == KingdomPerks.Blocker.NONE) {
            status = this.canResearch()
                    ? Component.translatable(tier == 0 ? "kingdoms.research.screen.click_unlock" : "kingdoms.research.screen.click_upgrade")
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("kingdoms.kingdom.screen.perks.leader_only").withStyle(ChatFormatting.RED);
        } else {
            KingdomPerk.Tier next = perk.tier(Math.min(tier + 1, perk.maxTier()));
            ChatFormatting color = blocker == KingdomPerks.Blocker.MAXED ? ChatFormatting.GOLD
                    : blocker == KingdomPerks.Blocker.RESEARCHING ? ChatFormatting.AQUA : ChatFormatting.RED;
            status = Component.translatable(blocker.translationKey() + ".short", next.requiredLevel(), next.cost()).withStyle(color);
        }
        lines.add(status);
        return lines;
    }

    /** What's being researched, a bar with how far along it is, and how long is left at the current pace. */
    private void renderProgress(GuiGraphics graphics, int left, int top) {
        int textWidth = WINDOW_WIDTH - 76;
        Optional<ResearchViewPayload.Progress> research = this.view.research();
        if (research.isEmpty()) {
            Component idle = Component.translatable(this.canResearch()
                    ? "kingdoms.research.screen.idle" : "kingdoms.research.screen.idle.member");
            graphics.drawWordWrap(this.font, idle, left, top + 4, WINDOW_WIDTH, 0xA0A0A0);
            return;
        }
        ResearchViewPayload.Progress progress = research.get();
        Component perk = KingdomPerks.byId(progress.perk()).map(KingdomPerk::displayName).orElse(Component.literal(progress.perk().toString()));
        graphics.drawString(this.font, Component.translatable("kingdoms.research.screen.researching", perk, progress.tier()), left, top, 0xFFFFFF);
        int barTop = top + 11;
        graphics.fill(left - 1, barTop - 1, left + textWidth + 1, barTop + BAR_HEIGHT + 1, 0xFF000000);
        graphics.fill(left, barTop, left + textWidth, barTop + BAR_HEIGHT, 0xFF303030);
        int filled = progress.duration() <= 0 ? textWidth : (int) ((long) textWidth * Math.min(progress.progress(), progress.duration()) / progress.duration());
        graphics.fill(left, barTop, left + filled, barTop + BAR_HEIGHT, 0xFF55AAFF);
        int percent = progress.duration() <= 0 ? 100 : (int) (100L * progress.progress() / progress.duration());
        Component pace;
        if (this.view.scholars() > 0) {
            int seconds = (progress.duration() - progress.progress() + this.view.scholars() - 1) / this.view.scholars();
            pace = Component.translatable("kingdoms.research.screen.time_left", percent, seconds / 60, String.format("%02d", seconds % 60));
        } else {
            pace = Component.translatable("kingdoms.research.screen.paused", percent).withStyle(ChatFormatting.RED);
        }
        graphics.drawWordWrap(this.font, pace, left, barTop + BAR_HEIGHT + 3, WINDOW_WIDTH, 0xA0A0A0);
    }

    // ---- Input: drag to pan, click a perk to research it ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isInside(mouseX, mouseY)) {
            this.pressedInside = true;
            this.dragged = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.pressedInside) {
            if (dragX != 0 || dragY != 0) {
                this.dragged = true;
                this.scroll(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.pressedInside) {
            this.pressedInside = false;
            if (!this.dragged) {
                this.perkAt(mouseX, mouseY).filter(this::canStart).ifPresent(perk -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    this.send(ResearchActionPayload.Action.START, perk.id().toString());
                });
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInside(mouseX, mouseY)) {
            this.scroll(scrollX * 16, scrollY * 16);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Moves the tree, keeping it from being dragged out of the window. Does nothing along an axis it fits on. */
    private void scroll(double dx, double dy) {
        this.scrollX = Mth.clamp(this.scrollX + dx, Math.min(0, INSIDE_WIDTH - this.treeWidth()), 0);
        this.scrollY = Mth.clamp(this.scrollY + dy, Math.min(0, INSIDE_HEIGHT - this.treeHeight()), 0);
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
}
