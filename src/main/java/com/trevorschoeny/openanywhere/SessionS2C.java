package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server opened or closed an item. On open, {@code menuSlot} is the opened item's
 * slot on the now-open menu (-1 when it isn't on it); the client mirrors the session,
 * which locks and greys that slot and shows its tab; {@code lane} names the tab
 * ({@link Session#lane()}). On close,
 * {@code toInventory} tells the client to step back to its inventory screen.
 */
public record SessionS2C(Openable kind, int lane, boolean open, int menuSlot, boolean toInventory) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SessionS2C> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "session"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SessionS2C> CODEC = StreamCodec.composite(
            Openable.STREAM_CODEC, SessionS2C::kind,
            ByteBufCodecs.VAR_INT, SessionS2C::lane,
            ByteBufCodecs.BOOL, SessionS2C::open,
            ByteBufCodecs.VAR_INT, SessionS2C::menuSlot,
            ByteBufCodecs.BOOL, SessionS2C::toInventory,
            SessionS2C::new);

    static SessionS2C opened(Openable kind, int lane, int menuSlot) {
        return new SessionS2C(kind, lane, true, menuSlot, false);
    }

    static SessionS2C closed(Openable kind, int lane, boolean toInventory) {
        return new SessionS2C(kind, lane, false, -1, toInventory);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
