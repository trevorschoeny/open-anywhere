package com.trevorschoeny.shulkersininventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player clicked a panel's close button. */
public record CloseC2S(Openable kind) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseC2S> CODEC =
            ByteBufCodecs.VAR_INT.map(i -> Openable.values()[i], Openable::ordinal).map(CloseC2S::new, CloseC2S::kind).cast();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
