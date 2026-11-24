package com.luckyowlstudios.kingdoms.client.screen;

import com.luckyowlstudios.kingdoms.kingdom.KingdomRank;
import com.luckyowlstudios.kingdoms.network.KingdomActionPayload;
import com.luckyowlstudios.kingdoms.network.KingdomActionPayload.Action;
import com.luckyowlstudios.kingdoms.network.KingdomViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Opened from the Town Hall's Members tab: every player online who isn't already in the kingdom, with a search
 * bar to filter them. Clicking a name sends them an invite. The list comes from the client's tab list, so it
 * keeps up as players join and leave.
 */
public class InvitePlayersScreen extends Screen {

    private static final int LIST_WIDTH = 220;
    private static final int ROW_HEIGHT = 20;

    private final KingdomScreen parent;
    private KingdomViewPayload view;
    /** Players invited from this screen, so each shows as sent and isn't invited twice by a double click. */
    private final Set<UUID> invited = new HashSet<>();
    private Set<UUID> lastOnline = Set.of();
    private String search = "";

    private EditBox searchBox;
    private PlayerList list;

    public InvitePlayersScreen(KingdomScreen parent, KingdomViewPayload view) {
        super(Component.translatable("kingdoms.kingdom.screen.invite_players.title"));
        this.parent = parent;
        this.view = view;
    }

    public KingdomScreen parent() {
        return this.parent;
    }

    /** New kingdom data from the server, e.g. after someone joined: drop anyone who is now a member. */
    void update(KingdomViewPayload view) {
        this.view = view;
        this.refresh();
    }

    private boolean canInvite() {
        return this.view.viewerRank().atLeast(KingdomRank.OFFICER);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - LIST_WIDTH / 2;
        this.searchBox = new EditBox(this.font, left, 24, LIST_WIDTH, 20, Component.translatable("kingdoms.kingdom.screen.invite_players.search"));
        this.searchBox.setHint(Component.translatable("kingdoms.kingdom.screen.invite_players.search"));
        this.searchBox.setMaxLength(16);
        this.searchBox.setValue(this.search);
        this.searchBox.setResponder(value -> {
            this.search = value;
            this.refresh();
        });
        this.addRenderableWidget(this.searchBox);

        this.list = this.addRenderableWidget(new PlayerList(this.minecraft, LIST_WIDTH, this.height - 50 - 52, 50));
        this.list.setX(left);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
        this.setInitialFocus(this.searchBox);
        this.refresh();
    }

    /** Rebuilds the list from who is online now, the kingdom's members, and the search text. */
    private void refresh() {
        if (this.list == null) {
            return;
        }
        Set<UUID> members = new HashSet<>();
        Set<UUID> banned = new HashSet<>();
        for (KingdomViewPayload.Member member : this.view.members()) {
            (member.banned() ? banned : members).add(member.id());
        }
        UUID self = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        String filter = this.search.strip().toLowerCase(Locale.ROOT);
        List<PlayerInfo> players = this.onlinePlayers().stream()
                .filter(info -> !info.getProfile().getId().equals(self) && !members.contains(info.getProfile().getId()))
                .filter(info -> filter.isEmpty() || info.getProfile().getName().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        this.list.setPlayers(players, banned);
        this.lastOnline = this.onlineIds();
    }

    private List<PlayerInfo> onlinePlayers() {
        ClientPacketListener connection = this.minecraft == null ? null : this.minecraft.getConnection();
        return connection == null ? List.of() : List.copyOf(connection.getOnlinePlayers());
    }

    private Set<UUID> onlineIds() {
        Set<UUID> ids = new HashSet<>();
        this.onlinePlayers().forEach(info -> ids.add(info.getProfile().getId()));
        return ids;
    }

    @Override
    public void tick() {
        super.tick();
        // Players join and leave while the screen is open; refresh when the tab list changes.
        if (!this.onlineIds().equals(this.lastOnline)) {
            this.refresh();
        }
    }

    private void invite(PlayerInfo player) {
        UUID id = player.getProfile().getId();
        if (!this.canInvite() || this.invited.contains(id)) {
            return;
        }
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new ServerboundCustomPayloadPacket(
                    new KingdomActionPayload(Action.INVITE, Optional.of(id), player.getProfile().getName(), 0)));
            this.invited.add(id);
        }
    }

    /** Vanilla's menu blur plus a dark tint, so the kingdom UI stands out from the world behind it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        if (this.list.children().isEmpty()) {
            Component empty = this.search.isBlank()
                    ? Component.translatable("kingdoms.kingdom.screen.invite_players.none_online")
                    : Component.translatable("kingdoms.kingdom.screen.invite_players.no_match", this.search.strip());
            graphics.drawCenteredString(this.font, empty, this.width / 2, 60, 0x808080);
        }
        if (!this.canInvite()) {
            graphics.drawCenteredString(this.font, Component.translatable("kingdoms.kingdom.screen.invite_players.officer_only"),
                    this.width / 2, this.height - 44, 0xFF8080);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class PlayerList extends ObjectSelectionList<PlayerList.Entry> {

        PlayerList(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW_HEIGHT);
        }

        void setPlayers(List<PlayerInfo> players, Set<UUID> banned) {
            this.replaceEntries(players.stream().map(info -> new Entry(info, banned.contains(info.getProfile().getId()))).toList());
            this.setScrollAmount(this.getScrollAmount());
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

            private final PlayerInfo player;
            private final boolean banned;

            Entry(PlayerInfo player, boolean banned) {
                this.player = player;
                this.banned = banned;
            }

            private boolean invited() {
                return InvitePlayersScreen.this.invited.contains(this.player.getProfile().getId());
            }

            private boolean clickable() {
                return !this.banned && !this.invited() && InvitePlayersScreen.this.canInvite();
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                if (hovering && this.clickable()) {
                    graphics.fill(left, top, left + width, top + height, 0x30FFFFFF);
                }
                PlayerFaceRenderer.draw(graphics, this.player.getSkin(), left + 2, top + 2, 16);
                graphics.drawString(InvitePlayersScreen.this.font, this.player.getProfile().getName(), left + 24, top + 6,
                        this.clickable() ? 0xFFFFFF : 0x808080);
                Component status = this.banned ? Component.translatable("kingdoms.kingdom.rank.banned")
                        : this.invited() ? Component.translatable("kingdoms.kingdom.screen.invite_players.sent")
                        : hovering && this.clickable() ? Component.translatable("kingdoms.kingdom.screen.invite") : null;
                if (status != null) {
                    int color = this.banned ? 0xFF5555 : this.invited() ? 0x55FF55 : 0xFFFF55;
                    graphics.drawString(InvitePlayersScreen.this.font, status,
                            left + width - 4 - InvitePlayersScreen.this.font.width(status), top + 6, color);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0 && this.clickable()) {
                    InvitePlayersScreen.this.invite(this.player);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.player.getProfile().getName());
            }
        }
    }
}
