package com.luckyowlstudios.kingdoms.network;

import com.luckyowlstudios.kingdoms.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.UUID;

/**
 * Server to client: how the viewer's kingdom stands with every other kingdom, for the Town Hall's Diplomacy tab.
 * Sent with the rest of the Town Hall screen, and to every online member of both kingdoms after a change.
 * The client ignores it unless the Town Hall screen is open.
 *
 * @param canManage whether the viewer's rank lets them change relations
 */
public record DiplomacyViewPayload(boolean canManage, List<Entry> kingdoms) implements CustomPacketPayload {

    public static final Type<DiplomacyViewPayload> TYPE = new Type<>(Constants.id("diplomacy_view"));

    /**
     * Another kingdom, as the viewer's kingdom sees it.
     *
     * @param weDeclaredEnemy   the viewer's kingdom has declared this one an enemy
     * @param theyDeclaredEnemy this kingdom has declared the viewer's kingdom an enemy
     * @param requestSent       the viewer's kingdom has asked this one for an alliance
     * @param requestReceived   this kingdom has asked the viewer's kingdom for an alliance
     */
    public record Entry(UUID id, String name, DyeColor color, boolean allied, boolean weDeclaredEnemy,
                        boolean theyDeclaredEnemy, boolean requestSent, boolean requestReceived) {

        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, entry.id);
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.name);
                    DyeColor.STREAM_CODEC.encode(buf, entry.color);
                    int flags = (entry.allied ? 1 : 0) | (entry.weDeclaredEnemy ? 2 : 0) | (entry.theyDeclaredEnemy ? 4 : 0)
                            | (entry.requestSent ? 8 : 0) | (entry.requestReceived ? 16 : 0);
                    buf.writeByte(flags);
                },
                buf -> {
                    UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                    String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                    DyeColor color = DyeColor.STREAM_CODEC.decode(buf);
                    int flags = buf.readByte();
                    return new Entry(id, name, color, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                            (flags & 8) != 0, (flags & 16) != 0);
                });
    }

    public static final DiplomacyViewPayload EMPTY = new DiplomacyViewPayload(false, List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, DiplomacyViewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DiplomacyViewPayload::canManage,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), DiplomacyViewPayload::kingdoms,
            DiplomacyViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
