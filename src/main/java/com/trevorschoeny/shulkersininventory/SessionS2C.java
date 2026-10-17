package com.trevorschoeny.shulkersininventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server opened ({@code menuSlot >= 0}) or closed ({@code -1}) a panel. The client
 * mirrors it, which shows the panel, locks the source slot and plays the animation.
 */
public record SessionS2C(Openable kind, int menuSlot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SessionS2C> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "session"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SessionS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Openable.values()[i], Openable::ordinal), SessionS2C::kind,
            ByteBufCodecs.VAR_INT, SessionS2C::menuSlot,
            SessionS2C::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
