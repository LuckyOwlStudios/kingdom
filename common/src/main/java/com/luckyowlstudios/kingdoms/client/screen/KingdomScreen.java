package com.luckyowlstudios.kingdoms.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerk;
import com.luckyowlstudios.kingdoms.kingdom.KingdomPerks;
import com.luckyowlstudios.kingdoms.kingdom.KingdomMaps;
import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import com.luckyowlstudios.kingdoms.network.CloseTownHallPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyActionPayload;
import com.luckyowlstudios.kingdoms.network.DiplomacyViewPayload;
import com.luckyowlstudios.kingdoms.network.KingdomActionPayload;
import com.luckyowlstudios.kingdoms.network.KingdomActionPayload.Action;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Town Hall screen for members of its kingdom: Overview, Members, Diplomacy, Customize and Settings tabs, using
 * vanilla's tab bar (as on the Create World screen) and vanilla widgets throughout.
 */
public class KingdomScreen extends Screen {

    private static final int MAP_SIZE = KingdomMapRenderer.MAP_SIZE;

    /** The latest relations from the server. Kept between screens: it arrives right after the kingdom view. */
    private static DiplomacyViewPayload diplomacyView = DiplomacyViewPayload.EMPTY;

    private KingdomViewPayload view;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabBar;
    private int selectedTab;
    private UUID selectedMember;
    private UUID selectedKingdom;

    private OverviewTab overview;
    private MembersTab members;
    private DiplomacyTab diplomacy;
    private CustomizeTab customize;
    private SettingsTab settings;
    private PerksTab perks;

    public KingdomScreen(KingdomViewPayload view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public static void show(KingdomViewPayload view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof KingdomScreen screen) {
            screen.update(view);
        } else if (minecraft.screen instanceof KingdomMapScreen map) {
            // Refresh the map in place, and the Town Hall behind it for when the map is closed.
            map.parent().view = view;
            map.update(view);
        } else if (minecraft.screen instanceof InvitePlayersScreen invite) {
            // An invite was sent from the player list: keep it open, and refresh the Town Hall behind it.
            invite.parent().view = view;
            invite.update(view);
        } else {
            minecraft.setScreen(new KingdomScreen(view));
        }
    }

    /** Relations from the server, when the Town Hall opens or after any change to them. */
    public static void showDiplomacy(DiplomacyViewPayload view) {
        diplomacyView = view;
        if (Minecraft.getInstance().screen instanceof KingdomScreen screen) {
            screen.rebuildWidgets();
        }
    }

    /** New data from the server after a change: rebuild in place, keeping the tab and selection. */
    void update(KingdomViewPayload view) {
        this.view = view;
        this.rebuildWidgets();
    }

    private boolean isLeader() {
        return this.view.viewerRank() == KingdomRank.LEADER;
    }

    private boolean isOfficer() {
        return this.view.viewerRank().atLeast(KingdomRank.OFFICER);
    }

    private void send(DiplomacyActionPayload action) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(action));
        }
    }

    private void send(KingdomActionPayload action) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(action));
        }
    }

    private Component coloredName() {
        return Component.literal(this.view.name()).withStyle(style -> style.withColor(this.view.color().getTextColor()));
    }

    @Override
    protected void init() {
        this.overview = new OverviewTab();
        this.members = new MembersTab();
        this.diplomacy = new DiplomacyTab();
        this.customize = new CustomizeTab();
        this.settings = new SettingsTab();
        this.perks = new PerksTab();
        this.tabBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(this.overview, this.members, this.perks, this.diplomacy, this.customize, this.settings)
                .build();
        this.addRenderableWidget(this.tabBar);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
        this.tabBar.selectTab(this.selectedTab, false);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.tabBar != null) {
            this.tabBar.setWidth(this.width);
            this.tabBar.arrangeElements();
            int top = this.tabBar.getRectangle().bottom();
            this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - 36 - top));
        }
    }

    @Override
    public void tick() {
        super.tick();
        Tab current = this.tabManager.getCurrentTab();
        if (current != null) {
            this.selectedTab = List.of(this.overview, this.members, this.perks, this.diplomacy, this.customize, this.settings).indexOf(current);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.tabManager.getCurrentTab() instanceof KingdomTab tab) {
            tab.renderExtras(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.tabManager.getCurrentTab() == this.overview && this.overview.isOverMap(mouseX, mouseY)
                && this.minecraft != null) {
            this.minecraft.setScreen(new KingdomMapScreen(this, this.view));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Closed (or replaced by another screen): let the server close the book on the Town Hall. */
    @Override
    public void removed() {
        super.removed();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new CloseTownHallPayload(this.view.townHall())));
        }
    }

    // ---- Tabs ----

    /** A tab that lays out its own widgets and can draw labels and pictures over the screen. */
    private abstract class KingdomTab implements Tab {

        private final Component title;
        protected final List<AbstractWidget> widgets = new ArrayList<>();
        protected ScreenRectangle area = ScreenRectangle.empty();

        KingdomTab(String key) {
            this.title = Component.translatable(key);
        }

        protected <T extends AbstractWidget> T add(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Component getTabTitle() {
            return this.title;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            this.widgets.forEach(consumer);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            this.area = area;
            this.layout(area.left() + area.width() / 2, area.top() + 8);
        }

        /** Places the widgets, given the centre of the tab area and the top to start from. */
        protected abstract void layout(int centerX, int top);

        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
        }

        protected int centerX() {
            return this.area.left() + this.area.width() / 2;
        }

        protected int top() {
            return this.area.top() + 8;
        }
    }

    /**
     * The kingdom at a glance. From the top: its name, its level with an XP bar, then two columns: on the left the
     * banner and who's who, the kingdom's numbers and the Leave button; on the right the kingdom map.
     */
    private final class OverviewTab extends KingdomTab {

        /** Room at the top for the name and the level bar; the columns start below it. */
        private static final int HEADER = 40;
        private static final int COLUMN_WIDTH = 160;
        private static final int GAP = 20;
        private static final ResourceLocation XP_BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
        private static final ResourceLocation XP_BAR_PROGRESS = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
        private static final int XP_BAR_WIDTH = 182;
        private static final int XP_BAR_HEIGHT = 5;
        private static final int LABEL = 0xA0A0A0;

        private final Button leave;

        OverviewTab() {
            super("kingdoms.kingdom.screen.overview");
            this.leave = this.add(Button.builder(Component.translatable("kingdoms.kingdom.screen.leave"),
                    button -> {
                        KingdomScreen.this.send(KingdomActionPayload.of(Action.LEAVE));
                        KingdomScreen.this.onClose();
                    }).size(COLUMN_WIDTH, 20).build());
        }

        /** The top of the two columns. */
        @Override
        protected int top() {
            return super.top() + HEADER;
        }

        @Override
        protected void layout(int centerX, int top) {
            // Level with the bottom of the map.
            this.leave.setPosition(this.left(), top + HEADER + MAP_SIZE - 20);
        }

        /** Left edge of the info column. The two columns together are centred in the tab. */
        int left() {
            return this.centerX() - (COLUMN_WIDTH + GAP + MAP_SIZE) / 2;
        }

        /** The kingdom's name, large and in its colour, centred at the top. */
        private void renderName(GuiGraphics graphics) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(this.centerX(), super.top(), 0);
            pose.scale(1.5F, 1.5F, 1.0F);
            graphics.drawCenteredString(KingdomScreen.this.font, KingdomScreen.this.coloredName(), 0, 0, 0xFFFFFF);
            pose.popPose();
        }

        /**
         * The level, and vanilla's experience bar filled with the XP toward the next one. Hovering shows the numbers.
         */
        private void renderLevelBar(GuiGraphics graphics, int mouseX, int mouseY) {
            KingdomViewPayload.Growth growth = KingdomScreen.this.view.growth();
            int center = this.centerX();
            int labelTop = super.top() + 17;
            graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.level",
                    growth.level()), center, labelTop, 0x80FF20);
            int barLeft = center - XP_BAR_WIDTH / 2;
            int barTop = labelTop + 11;
            graphics.blitSprite(XP_BAR_BACKGROUND, barLeft, barTop, XP_BAR_WIDTH, XP_BAR_HEIGHT);
            int filled = growth.isMax() ? XP_BAR_WIDTH
                    : growth.xpToNext() <= 0 ? 0 : (int) ((long) XP_BAR_WIDTH * Math.min(growth.xp(), growth.xpToNext()) / growth.xpToNext());
            if (filled > 0) {
                graphics.blitSprite(XP_BAR_PROGRESS, XP_BAR_WIDTH, XP_BAR_HEIGHT, 0, 0, barLeft, barTop, filled, XP_BAR_HEIGHT);
            }
            if (mouseX >= barLeft && mouseX < barLeft + XP_BAR_WIDTH && mouseY >= labelTop && mouseY < barTop + XP_BAR_HEIGHT + 2) {
                List<Component> lines = new ArrayList<>();
                lines.add(growth.isMax()
                        ? Component.translatable("kingdoms.kingdom.screen.level_bar.max")
                        : Component.translatable("kingdoms.kingdom.screen.level_bar", growth.xp(), growth.xpToNext(), growth.level() + 1));
                lines.add(Component.translatable("kingdoms.kingdom.screen.level_bar.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
                List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
                lines.forEach(line -> wrapped.addAll(KingdomScreen.this.font.split(line, 200)));
                graphics.renderTooltip(KingdomScreen.this.font, wrapped, mouseX, mouseY);
            }
        }

        @Override
        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
            KingdomViewPayload view = KingdomScreen.this.view;
            KingdomViewPayload.Growth growth = view.growth();
            int top = this.top();
            int left = this.left();
            this.renderName(graphics);
            this.renderLevelBar(graphics, mouseX, mouseY);

            // Banner, drawn as a big item like vanilla's loom preview.
            ItemStack banner = view.banner();
            graphics.fill(left, top, left + 52, top + 52, 0xFF000000);
            graphics.fill(left + 1, top + 1, left + 51, top + 51, 0xFF8B8B8B);
            if (!banner.isEmpty()) {
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(left + 2, top + 2, 0);
                pose.scale(3.0F, 3.0F, 1.0F);
                graphics.renderItem(banner, 0, 0);
                pose.popPose();
            } else {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.no_banner"),
                        left + 26, top + 22, LABEL);
            }

            // Who's who, beside the banner.
            int textLeft = left + 60;
            String leader = view.members().stream().filter(m -> m.rank() == KingdomRank.LEADER)
                    .map(KingdomViewPayload.Member::name).findFirst().orElse("?");
            long count = view.members().stream().filter(m -> !m.banned()).count();
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.info.leader", leader), textLeft, top + 2, LABEL);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.member_count", count), textLeft, top + 14, LABEL);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.your_rank",
                    Component.translatable(view.viewerRank().translationKey())), textLeft, top + 26, LABEL);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.color",
                    ColorSwatchButton.name(view.color())), textLeft, top + 38, LABEL);

            // The kingdom's numbers, under the banner.
            graphics.fill(left, top + 60, left + COLUMN_WIDTH, top + 61, 0x40FFFFFF);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.villagers",
                    growth.villagers(), growth.villagerCap()), left, top + 68, LABEL);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.land",
                    view.claimCount(), growth.landCap()), left, top + 80, LABEL);
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.map_scale",
                    view.mapRadius(), KingdomMaps.MAX_RADIUS), left, top + 92, LABEL);

            // The kingdom map, on vanilla's map background. Clicking it opens the full map screen.
            // It shows the middle map, around the Town Hall, at full size; the rest is on the full map screen.
            int mapLeft = this.mapLeft();
            KingdomMapRenderer.drawPaper(graphics, mapLeft, top, MAP_SIZE, 1.0F);
            int gridLeft = mapLeft - view.mapRadius() * MAP_SIZE;
            int gridTop = top - view.mapRadius() * MAP_SIZE;
            graphics.enableScissor(mapLeft, top, mapLeft + MAP_SIZE, top + MAP_SIZE);
            KingdomMapRenderer.drawMap(graphics, view, gridLeft, gridTop, 1.0F);
            graphics.disableScissor();
            boolean overHall = false;
            if (KingdomMapRenderer.hasMapData(view)) {
                for (KingdomMapRenderer.Icon icon : KingdomMapRenderer.icons(view)) {
                    int x = gridLeft + Math.round(icon.mapX());
                    int y = gridTop + Math.round(icon.mapY());
                    KingdomMapRenderer.drawIcon(graphics, icon, x, y, KingdomMapRenderer.ICON_SIZE, false);
                    overHall |= Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5;
                }
            }
            if (overHall) {
                graphics.renderTooltip(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.town_hall_of",
                        KingdomScreen.this.coloredName()), mouseX, mouseY);
            } else if (this.isOverMap(mouseX, mouseY)) {
                graphics.renderComponentTooltip(KingdomScreen.this.font, List.of(
                        Component.translatable("kingdoms.kingdom.screen.map_open"),
                        Component.translatable("kingdoms.kingdom.screen.map_hint").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
            }
        }

        int mapLeft() {
            return this.left() + COLUMN_WIDTH + GAP;
        }

        boolean isOverMap(double mouseX, double mouseY) {
            int left = this.mapLeft();
            int top = this.top();
            return mouseX >= left && mouseX < left + MAP_SIZE && mouseY >= top && mouseY < top + MAP_SIZE;
        }
    }

    /** Everyone in the kingdom, and the ban list, with rank buttons and a button to invite online players. */
    private final class MembersTab extends KingdomTab {

        private final MemberList list;
        private final Button promote;
        private final Button demote;
        private final Button kick;
        private final Button ban;
        private final Button unban;
        private final Button invite;

        MembersTab() {
            super("kingdoms.kingdom.screen.members");
            this.list = this.add(new MemberList());
            this.promote = this.add(this.memberButton("kingdoms.kingdom.screen.promote", Action.PROMOTE));
            this.demote = this.add(this.memberButton("kingdoms.kingdom.screen.demote", Action.DEMOTE));
            this.kick = this.add(this.memberButton("kingdoms.kingdom.screen.kick", Action.KICK));
            this.ban = this.add(this.memberButton("kingdoms.kingdom.screen.ban", Action.BAN));
            this.unban = this.add(this.memberButton("kingdoms.kingdom.screen.unban", Action.UNBAN));
            this.invite = this.add(Button.builder(Component.translatable("kingdoms.kingdom.screen.invite_players"),
                    button -> Minecraft.getInstance().setScreen(new InvitePlayersScreen(KingdomScreen.this, KingdomScreen.this.view)))
                    .size(220, 20).build());
            this.updateButtons();
        }

        private Button memberButton(String key, Action action) {
            return Button.builder(Component.translatable(key), button -> {
                if (KingdomScreen.this.selectedMember != null) {
                    KingdomScreen.this.send(KingdomActionPayload.on(action, KingdomScreen.this.selectedMember));
                }
            }).size(80, 20).build();
        }

        @Override
        protected void layout(int centerX, int top) {
            int listWidth = 220;
            int listLeft = centerX - (listWidth + 8 + 80) / 2;
            int listHeight = Math.max(60, this.area.height() - 46);
            this.list.setRectangle(listWidth, listHeight, listLeft, top);
            int buttonLeft = listLeft + listWidth + 8;
            Button[] buttons = {this.promote, this.demote, this.kick, this.ban, this.unban};
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setPosition(buttonLeft, top + i * 24);
            }
            this.invite.setPosition(listLeft, top + listHeight + 6);
        }

        void updateButtons() {
            KingdomViewPayload.Member member = KingdomScreen.this.view.members().stream()
                    .filter(m -> m.id().equals(KingdomScreen.this.selectedMember)).findFirst().orElse(null);
            KingdomRank viewer = KingdomScreen.this.view.viewerRank();
            boolean outranked = member != null && !member.banned() && viewer.ordinal() > member.rank().ordinal();
            this.promote.active = KingdomScreen.this.isLeader() && member != null && !member.banned()
                    && (member.rank() == KingdomRank.MEMBER || member.rank() == KingdomRank.OFFICER);
            this.promote.setTooltip(member != null && member.rank() == KingdomRank.OFFICER
                    ? Tooltip.create(Component.translatable("kingdoms.kingdom.screen.promote_to_leader")) : null);
            this.demote.active = KingdomScreen.this.isLeader() && member != null && member.rank() == KingdomRank.OFFICER;
            this.kick.active = KingdomScreen.this.isOfficer() && outranked;
            this.ban.active = KingdomScreen.this.isOfficer() && outranked;
            this.unban.active = KingdomScreen.this.isOfficer() && member != null && member.banned();
            this.invite.active = KingdomScreen.this.isOfficer();
            this.invite.setTooltip(KingdomScreen.this.isOfficer() ? null
                    : Tooltip.create(Component.translatable("kingdoms.kingdom.screen.invite_players.officer_only")));
        }

        private final class MemberList extends ObjectSelectionList<MemberList.Entry> {

            MemberList() {
                super(Minecraft.getInstance(), 220, 100, 0, 16);
                for (KingdomViewPayload.Member member : KingdomScreen.this.view.members()) {
                    Entry entry = new Entry(member);
                    this.addEntry(entry);
                    if (member.id().equals(KingdomScreen.this.selectedMember)) {
                        this.setSelected(entry);
                    }
                }
            }

            @Override
            public void setSelected(Entry entry) {
                super.setSelected(entry);
                KingdomScreen.this.selectedMember = entry == null ? null : entry.member.id();
                if (KingdomScreen.this.members != null) {
                    KingdomScreen.this.members.updateButtons();
                }
            }

            @Override
            public int getRowWidth() {
                return this.width - 12;
            }

            @Override
            protected int getScrollbarPosition() {
                return this.getX() + this.width - 6;
            }

            private final class Entry extends ObjectSelectionList.Entry<Entry> {

                private final KingdomViewPayload.Member member;

                Entry(KingdomViewPayload.Member member) {
                    this.member = member;
                }

                @Override
                public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                   int mouseX, int mouseY, boolean hovering, float partialTick) {
                    graphics.drawString(KingdomScreen.this.font, this.member.name(), left + 2, top + 3,
                            this.member.banned() ? 0x808080 : 0xFFFFFF);
                    Component rank = this.member.banned()
                            ? Component.translatable("kingdoms.kingdom.rank.banned")
                            : Component.translatable(this.member.rank().translationKey());
                    int color = this.member.banned() ? 0xFF5555 : this.member.rank() == KingdomRank.LEADER ? 0xFFD700
                            : this.member.rank() == KingdomRank.OFFICER ? 0x55FFFF : 0xA0A0A0;
                    graphics.drawString(KingdomScreen.this.font, rank, left + width - 4 - KingdomScreen.this.font.width(rank), top + 3, color);
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    MemberList.this.setSelected(this);
                    return true;
                }

                @Override
                public Component getNarration() {
                    return Component.literal(this.member.name());
                }
            }
        }
    }

    /** Change the colour, and pick a banner from your inventory. Leader only. */
    private final class CustomizeTab extends KingdomTab {

        private final List<ColorSwatchButton> swatches = new ArrayList<>();
        private final List<BannerButton> banners = new ArrayList<>();

        CustomizeTab() {
            super("kingdoms.kingdom.screen.customize");
            for (DyeColor color : DyeColor.values()) {
                ColorSwatchButton swatch = this.add(new ColorSwatchButton(0, 0, color, () -> KingdomScreen.this.view.color(),
                        picked -> KingdomScreen.this.send(KingdomActionPayload.withValue(Action.SET_COLOR, picked.getId()))));
                swatch.active = KingdomScreen.this.isLeader();
                this.swatches.add(swatch);
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                var inventory = minecraft.player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize() && this.banners.size() < 18; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.getItem() instanceof BannerItem) {
                        BannerButton button = this.add(new BannerButton(stack, slot));
                        button.active = KingdomScreen.this.isLeader();
                        this.banners.add(button);
                    }
                }
            }
        }

        @Override
        protected void layout(int centerX, int top) {
            int left = centerX - ColorSwatchButton.ROW_WIDTH / 2;
            for (int i = 0; i < this.swatches.size(); i++) {
                this.swatches.get(i).setPosition(left + (i % ColorSwatchButton.PER_ROW) * (ColorSwatchButton.SIZE + ColorSwatchButton.GAP),
                        top + 14 + (i / ColorSwatchButton.PER_ROW) * (ColorSwatchButton.SIZE + ColorSwatchButton.GAP));
            }
            int bannerTop = top + 14 + 2 * (ColorSwatchButton.SIZE + ColorSwatchButton.GAP) + 22;
            for (int i = 0; i < this.banners.size(); i++) {
                this.banners.get(i).setPosition(left + (i % 9) * 22, bannerTop + (i / 9) * 22);
            }
        }

        @Override
        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
            int left = this.centerX() - ColorSwatchButton.ROW_WIDTH / 2;
            int top = this.top();
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.create.color"), left, top, 0xA0A0A0);
            int bannerTop = top + 14 + 2 * (ColorSwatchButton.SIZE + ColorSwatchButton.GAP) + 8;
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.banner"), left, bannerTop, 0xA0A0A0);
            if (this.banners.isEmpty()) {
                graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.no_banners_held"),
                        left, bannerTop + 18, 0x808080);
            }
            if (!KingdomScreen.this.isLeader()) {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.leader_only"),
                        this.centerX(), this.area.bottom() - 14, 0xFF8080);
            }
        }

        /** A banner from the inventory, drawn in a slot; pressing it makes it the kingdom's banner. */
        private final class BannerButton extends net.minecraft.client.gui.components.AbstractButton {

            private final ItemStack stack;
            private final int slot;

            BannerButton(ItemStack stack, int slot) {
                super(0, 0, 20, 20, stack.getHoverName());
                this.stack = stack;
                this.slot = slot;
                this.setTooltip(Tooltip.create(stack.getHoverName()));
            }

            @Override
            public void onPress() {
                KingdomScreen.this.send(KingdomActionPayload.withValue(Action.SET_BANNER, this.slot));
            }

            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                graphics.fill(x, y, x + 20, y + 20, 0xFF000000);
                graphics.fill(x + 1, y + 1, x + 19, y + 19, this.isHoveredOrFocused() && this.active ? 0xFFC6C6C6 : 0xFF8B8B8B);
                graphics.renderItem(this.stack, x + 2, y + 2);
                if (ItemStack.isSameItemSameComponents(this.stack, KingdomScreen.this.view.banner())) {
                    graphics.renderOutline(x, y, 20, 20, 0xFFFFFFFF);
                }
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
                this.defaultButtonNarrationText(output);
            }
        }
    }

    /** Kingdom preferences. Leader only. */
    private final class SettingsTab extends KingdomTab {

        private final CycleButton<Boolean> inviteOnly;
        private final CycleButton<Boolean> friendlyFire;

        SettingsTab() {
            super("kingdoms.kingdom.screen.settings");
            this.inviteOnly = this.add(CycleButton.onOffBuilder(KingdomScreen.this.view.inviteOnly())
                    .withTooltip(value -> Tooltip.create(Component.translatable("kingdoms.kingdom.screen.invite_only.tooltip")))
                    .create(0, 0, 210, 20, Component.translatable("kingdoms.kingdom.screen.invite_only"),
                            (button, value) -> KingdomScreen.this.send(KingdomActionPayload.withValue(Action.SET_INVITE_ONLY, value ? 1 : 0))));
            this.friendlyFire = this.add(CycleButton.onOffBuilder(KingdomScreen.this.view.friendlyFire())
                    .withTooltip(value -> Tooltip.create(Component.translatable("kingdoms.kingdom.screen.friendly_fire.tooltip")))
                    .create(0, 0, 210, 20, Component.translatable("kingdoms.kingdom.screen.friendly_fire"),
                            (button, value) -> KingdomScreen.this.send(KingdomActionPayload.withValue(Action.SET_FRIENDLY_FIRE, value ? 1 : 0))));
            this.inviteOnly.active = KingdomScreen.this.isLeader();
            this.friendlyFire.active = KingdomScreen.this.isLeader();
        }

        @Override
        protected void layout(int centerX, int top) {
            this.inviteOnly.setPosition(centerX - 105, top + 8);
            this.friendlyFire.setPosition(centerX - 105, top + 32);
        }

        @Override
        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!KingdomScreen.this.isLeader()) {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.leader_only"),
                        this.centerX(), this.top() + 64, 0xFF8080);
            }
        }
    }

    /**
     * Every other kingdom and how this one stands with it: request, accept or end alliances, and declare or
     * drop enemies. Officers and the leader can act; everyone else can look.
     */
    private final class DiplomacyTab extends KingdomTab {

        private static final int LIST_WIDTH = 220;
        private static final int BUTTON_WIDTH = 110;

        private final KingdomList list;
        private final Button alliance;
        private final Button decline;
        private final Button enemy;

        DiplomacyTab() {
            super("kingdoms.diplomacy.screen.title");
            this.list = this.add(new KingdomList());
            this.alliance = this.add(Button.builder(Component.translatable("kingdoms.diplomacy.screen.request"), button -> {
                DiplomacyViewPayload.Entry entry = this.selected();
                if (entry != null) {
                    KingdomScreen.this.send(new DiplomacyActionPayload(allianceAction(entry), entry.id()));
                }
            }).size(BUTTON_WIDTH, 20).build());
            this.decline = this.add(Button.builder(Component.translatable("kingdoms.diplomacy.screen.decline"), button -> {
                DiplomacyViewPayload.Entry entry = this.selected();
                if (entry != null) {
                    KingdomScreen.this.send(new DiplomacyActionPayload(DiplomacyActionPayload.Action.DECLINE_ALLIANCE, entry.id()));
                }
            }).size(BUTTON_WIDTH, 20).build());
            this.enemy = this.add(Button.builder(Component.translatable("kingdoms.diplomacy.screen.declare_enemy"), button -> {
                DiplomacyViewPayload.Entry entry = this.selected();
                if (entry != null) {
                    KingdomScreen.this.send(new DiplomacyActionPayload(entry.weDeclaredEnemy()
                            ? DiplomacyActionPayload.Action.MAKE_PEACE : DiplomacyActionPayload.Action.DECLARE_ENEMY, entry.id()));
                }
            }).size(BUTTON_WIDTH, 20).build());
            this.updateButtons();
        }

        /** What the alliance button does for this kingdom right now. */
        private static DiplomacyActionPayload.Action allianceAction(DiplomacyViewPayload.Entry entry) {
            if (entry.allied()) return DiplomacyActionPayload.Action.END_ALLIANCE;
            if (entry.requestReceived()) return DiplomacyActionPayload.Action.ACCEPT_ALLIANCE;
            if (entry.requestSent()) return DiplomacyActionPayload.Action.CANCEL_REQUEST;
            return DiplomacyActionPayload.Action.REQUEST_ALLIANCE;
        }

        private DiplomacyViewPayload.Entry selected() {
            return diplomacyView.kingdoms().stream().filter(entry -> entry.id().equals(KingdomScreen.this.selectedKingdom))
                    .findFirst().orElse(null);
        }

        @Override
        protected void layout(int centerX, int top) {
            int listLeft = centerX - (LIST_WIDTH + 8 + BUTTON_WIDTH) / 2;
            int listHeight = Math.max(60, this.area.height() - 30);
            this.list.setRectangle(LIST_WIDTH, listHeight, listLeft, top);
            int buttonLeft = listLeft + LIST_WIDTH + 8;
            this.alliance.setPosition(buttonLeft, top);
            this.decline.setPosition(buttonLeft, top + 24);
            this.enemy.setPosition(buttonLeft, top + 56);
        }

        void updateButtons() {
            DiplomacyViewPayload.Entry entry = this.selected();
            boolean canAct = diplomacyView.canManage() && entry != null;
            DiplomacyActionPayload.Action action = entry == null ? DiplomacyActionPayload.Action.REQUEST_ALLIANCE : allianceAction(entry);
            this.alliance.setMessage(Component.translatable(switch (action) {
                case END_ALLIANCE -> "kingdoms.diplomacy.screen.end_alliance";
                case ACCEPT_ALLIANCE -> "kingdoms.diplomacy.screen.accept";
                case CANCEL_REQUEST -> "kingdoms.diplomacy.screen.cancel_request";
                default -> "kingdoms.diplomacy.screen.request";
            }));
            // You can't ask a kingdom you've declared an enemy for an alliance: make peace first.
            this.alliance.active = canAct && !(action == DiplomacyActionPayload.Action.REQUEST_ALLIANCE && entry.weDeclaredEnemy());
            this.alliance.setTooltip(entry != null && action == DiplomacyActionPayload.Action.REQUEST_ALLIANCE && entry.weDeclaredEnemy()
                    ? Tooltip.create(Component.translatable("kingdoms.diplomacy.screen.make_peace_first")) : null);
            this.decline.visible = entry != null && entry.requestReceived();
            this.decline.active = canAct;
            boolean peace = entry != null && entry.weDeclaredEnemy();
            this.enemy.setMessage(Component.translatable(peace ? "kingdoms.diplomacy.screen.make_peace" : "kingdoms.diplomacy.screen.declare_enemy"));
            this.enemy.setTooltip(Tooltip.create(Component.translatable(peace ? "kingdoms.diplomacy.screen.make_peace.tooltip"
                    : entry != null && entry.allied() ? "kingdoms.diplomacy.screen.declare_enemy.ally_tooltip"
                    : "kingdoms.diplomacy.screen.declare_enemy.tooltip")));
            this.enemy.active = canAct;
        }

        @Override
        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
            int listLeft = this.centerX() - (LIST_WIDTH + 8 + BUTTON_WIDTH) / 2;
            int bottom = this.top() + Math.max(60, this.area.height() - 30);
            if (diplomacyView.kingdoms().isEmpty()) {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.diplomacy.screen.no_kingdoms"),
                        listLeft + LIST_WIDTH / 2, this.top() + 20, 0x808080);
            }
            DiplomacyViewPayload.Entry entry = this.selected();
            if (entry != null) {
                graphics.drawWordWrap(KingdomScreen.this.font, describe(entry), listLeft + LIST_WIDTH + 8, this.top() + 84, BUTTON_WIDTH, 0xA0A0A0);
            }
            if (!diplomacyView.canManage()) {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.diplomacy.screen.officers_only"),
                        this.centerX(), bottom + 8, 0xFF8080);
            }
        }

        /** A sentence on where things stand with this kingdom. */
        private static Component describe(DiplomacyViewPayload.Entry entry) {
            if (entry.allied()) return Component.translatable("kingdoms.diplomacy.screen.describe.allied");
            if (entry.weDeclaredEnemy() && entry.theyDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.screen.describe.war");
            if (entry.weDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.screen.describe.enemy");
            if (entry.theyDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.screen.describe.hostile");
            if (entry.requestReceived()) return Component.translatable("kingdoms.diplomacy.screen.describe.request_received");
            if (entry.requestSent()) return Component.translatable("kingdoms.diplomacy.screen.describe.request_sent");
            return Component.translatable("kingdoms.diplomacy.screen.describe.neutral");
        }

        /** The short label shown in the list, and its colour. */
        private static Component label(DiplomacyViewPayload.Entry entry) {
            if (entry.allied()) return Component.translatable("kingdoms.diplomacy.relation.ally").withColor(0x55FF55);
            if (entry.weDeclaredEnemy() && entry.theyDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.relation.war").withColor(0xFF5555);
            if (entry.weDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.relation.enemy").withColor(0xFF5555);
            if (entry.theyDeclaredEnemy()) return Component.translatable("kingdoms.diplomacy.relation.hostile").withColor(0xFFAA00);
            if (entry.requestReceived()) return Component.translatable("kingdoms.diplomacy.relation.request_received").withColor(0xFFFF55);
            if (entry.requestSent()) return Component.translatable("kingdoms.diplomacy.relation.request_sent").withColor(0xC0C080);
            return Component.translatable("kingdoms.diplomacy.relation.neutral").withColor(0xA0A0A0);
        }

        private final class KingdomList extends ObjectSelectionList<KingdomList.Entry> {

            KingdomList() {
                super(Minecraft.getInstance(), LIST_WIDTH, 100, 0, 16);
                for (DiplomacyViewPayload.Entry kingdom : diplomacyView.kingdoms()) {
                    Entry entry = new Entry(kingdom);
                    this.addEntry(entry);
                    if (kingdom.id().equals(KingdomScreen.this.selectedKingdom)) {
                        this.setSelected(entry);
                    }
                }
            }

            @Override
            public void setSelected(Entry entry) {
                super.setSelected(entry);
                KingdomScreen.this.selectedKingdom = entry == null ? null : entry.kingdom.id();
                if (KingdomScreen.this.diplomacy != null) {
                    KingdomScreen.this.diplomacy.updateButtons();
                }
            }

            @Override
            public int getRowWidth() {
                return this.width - 12;
            }

            @Override
            protected int getScrollbarPosition() {
                return this.getX() + this.width - 6;
            }

            private final class Entry extends ObjectSelectionList.Entry<Entry> {

                private final DiplomacyViewPayload.Entry kingdom;

                Entry(DiplomacyViewPayload.Entry kingdom) {
                    this.kingdom = kingdom;
                }

                @Override
                public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                   int mouseX, int mouseY, boolean hovering, float partialTick) {
                    Component label = label(this.kingdom);
                    int labelWidth = KingdomScreen.this.font.width(label);
                    String name = KingdomScreen.this.font.plainSubstrByWidth(this.kingdom.name(), width - 12 - labelWidth);
                    graphics.drawString(KingdomScreen.this.font, name, left + 2, top + 3, this.kingdom.color().getTextColor());
                    graphics.drawString(KingdomScreen.this.font, label, left + width - 4 - labelWidth, top + 3, 0xFFFFFF);
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    KingdomList.this.setSelected(this);
                    return true;
                }

                @Override
                public Component getNarration() {
                    return Component.translatable("narrator.select", Component.literal(this.kingdom.name()).append(", ").append(label(this.kingdom)));
                }
            }
        }
    }

    /**
     * The kingdom perk tree. The leader spends perk points (earned with each level) to unlock perks and upgrade them
     * through their tiers; officers and the leader empty the tax treasury here too.
     */
    private final class PerksTab extends KingdomTab {

        private static final int NODE_SIZE = 26;
        private static final int COLUMN_GAP = 64;
        private static final int ROW_GAP = 44;

        private final List<PerkNode> nodes = new ArrayList<>();
        private final Button collect;

        PerksTab() {
            super("kingdoms.kingdom.screen.perks");
            for (KingdomPerk perk : KingdomPerks.all()) {
                this.nodes.add(this.add(new PerkNode(perk)));
            }
            KingdomViewPayload.Growth growth = KingdomScreen.this.view.growth();
            this.collect = this.add(Button.builder(Component.translatable("kingdoms.kingdom.screen.collect_taxes", growth.taxes()),
                    button -> KingdomScreen.this.send(KingdomActionPayload.of(Action.COLLECT_TAXES))).size(120, 20).build());
            this.collect.active = KingdomScreen.this.isOfficer() && growth.taxes() > 0;
            this.collect.visible = growth.taxCap() > 0 || growth.taxes() > 0;
            if (!KingdomScreen.this.isOfficer()) {
                this.collect.setTooltip(Tooltip.create(Component.translatable("kingdoms.kingdom.screen.collect_taxes.officer_only")));
            }
        }

        @Override
        protected void layout(int centerX, int top) {
            int columns = this.nodes.stream().mapToInt(node -> node.perk.column()).max().orElse(0) + 1;
            int treeLeft = centerX - ((columns - 1) * COLUMN_GAP + NODE_SIZE) / 2;
            int treeTop = top + 36;
            for (PerkNode node : this.nodes) {
                node.setPosition(treeLeft + node.perk.column() * COLUMN_GAP, treeTop + node.perk.row() * ROW_GAP);
            }
            this.collect.setPosition(centerX + 160 - 120, top);
        }

        @Override
        void renderExtras(GuiGraphics graphics, int mouseX, int mouseY) {
            KingdomViewPayload.Growth growth = KingdomScreen.this.view.growth();
            int left = this.centerX() - 160;
            int top = this.top();
            graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.perk_points",
                    growth.perkPoints(), KingdomPerks.POINTS_PER_LEVEL), left, top, 0xFFD700);
            if (this.collect.visible) {
                graphics.drawString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.treasury",
                        growth.taxes(), growth.taxCap()), left, top + 12, 0x55FF55);
            }
            if (!KingdomScreen.this.view.viewerRank().atLeast(KingdomPerks.UNLOCK_RANK)) {
                graphics.drawCenteredString(KingdomScreen.this.font, Component.translatable("kingdoms.kingdom.screen.perks.leader_only"),
                        this.centerX(), this.area.bottom() - 14, 0xFF8080);
            }

            // Lines from each perk's prerequisites, gold once the prerequisite is unlocked.
            for (PerkNode node : this.nodes) {
                for (ResourceLocation id : node.perk.prerequisites()) {
                    this.nodes.stream().filter(other -> other.perk.id().equals(id)).findFirst().ifPresent(from -> {
                        int color = growth.perkTier(id) > 0 ? 0xFFFFD700 : 0xFF606060;
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
                    List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                    node.tooltip().forEach(line -> lines.addAll(KingdomScreen.this.font.split(line, 220)));
                    graphics.renderTooltip(KingdomScreen.this.font, lines, mouseX, mouseY);
                }
            }
        }

        /** One perk in the tree: its icon, framed by how far along it is, and its tier underneath. */
        private final class PerkNode extends net.minecraft.client.gui.components.AbstractButton {

            private final KingdomPerk perk;

            PerkNode(KingdomPerk perk) {
                super(0, 0, NODE_SIZE, NODE_SIZE, perk.displayName());
                this.perk = perk;
                this.active = KingdomScreen.this.view.viewerRank().atLeast(KingdomPerks.UNLOCK_RANK)
                        && this.blocker() == KingdomPerks.Blocker.NONE;
            }

            private int tier() {
                return KingdomScreen.this.view.growth().perkTier(this.perk.id());
            }

            private KingdomPerks.Blocker blocker() {
                KingdomViewPayload.Growth growth = KingdomScreen.this.view.growth();
                return KingdomPerks.blocker(this.perk, growth.level(), growth.perkPoints(), growth::perkTier);
            }

            @Override
            public void onPress() {
                KingdomScreen.this.send(KingdomActionPayload.withText(Action.UNLOCK_PERK, this.perk.id().toString()));
            }

            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                int tier = this.tier();
                int frame = tier >= this.perk.maxTier() ? 0xFFFFD700 : tier > 0 ? 0xFFC0A040
                        : this.active ? 0xFF55FF55 : 0xFF404040;
                graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, frame);
                graphics.fill(x + 2, y + 2, x + NODE_SIZE - 2, y + NODE_SIZE - 2,
                        this.isHoveredOrFocused() ? 0xFF9A9A9A : 0xFF6B6B6B);
                graphics.renderItem(new ItemStack(this.perk.icon()), x + 5, y + 5);
                if (tier == 0) {
                    // Not unlocked yet: dim the icon.
                    graphics.fill(x + 2, y + 2, x + NODE_SIZE - 2, y + NODE_SIZE - 2, 0x90000000);
                }
                // One pip per tier, filled for the ones bought.
                int pipLeft = x + NODE_SIZE / 2 - (this.perk.maxTier() * 5 - 1) / 2;
                for (int n = 1; n <= this.perk.maxTier(); n++) {
                    int pipX = pipLeft + (n - 1) * 5;
                    graphics.fill(pipX, y + NODE_SIZE + 2, pipX + 4, y + NODE_SIZE + 6, n <= tier ? 0xFFFFD700 : 0xFF404040);
                }
                graphics.drawCenteredString(KingdomScreen.this.font, this.perk.displayName(), x + NODE_SIZE / 2, y + NODE_SIZE + 8,
                        tier > 0 ? 0xFFFFFF : 0xA0A0A0);
            }

            List<Component> tooltip() {
                List<Component> lines = new ArrayList<>();
                int tier = this.tier();
                lines.add(this.perk.displayName().copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                lines.add(this.perk.description().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                for (int n = 1; n <= this.perk.maxTier(); n++) {
                    KingdomPerk.Tier info = this.perk.tier(n);
                    net.minecraft.ChatFormatting color = n <= tier ? net.minecraft.ChatFormatting.GREEN
                            : n == tier + 1 ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.DARK_GRAY;
                    lines.add(Component.translatable("kingdoms.kingdom.screen.perk_tier", n, this.perk.tierDescription(n),
                            info.requiredLevel(), info.cost()).withStyle(color));
                }
                if (!this.perk.prerequisites().isEmpty()) {
                    Component names = net.minecraft.network.chat.ComponentUtils.formatList(this.perk.prerequisites().stream()
                            .map(id -> KingdomPerks.byId(id).map(KingdomPerk::displayName).orElse(Component.literal(id.toString())))
                            .toList(), Component.literal(", "));
                    lines.add(Component.translatable("kingdoms.kingdom.screen.perk_requires", names).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                KingdomPerks.Blocker blocker = this.blocker();
                Component status;
                if (blocker == KingdomPerks.Blocker.NONE) {
                    status = KingdomScreen.this.view.viewerRank().atLeast(KingdomPerks.UNLOCK_RANK)
                            ? Component.translatable(tier == 0 ? "kingdoms.kingdom.screen.perk_unlock" : "kingdoms.kingdom.screen.perk_upgrade")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN)
                            : Component.translatable("kingdoms.kingdom.screen.perks.leader_only").withStyle(net.minecraft.ChatFormatting.RED);
                } else {
                    KingdomPerk.Tier next = this.perk.tier(Math.min(tier + 1, this.perk.maxTier()));
                    status = Component.translatable(blocker.translationKey() + ".short", next.requiredLevel(), next.cost())
                            .withStyle(blocker == KingdomPerks.Blocker.MAXED ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED);
                }
                lines.add(status);
                return lines;
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
                this.defaultButtonNarrationText(output);
            }
        }
    }
}
