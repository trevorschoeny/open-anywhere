package com.trevorschoeny.shulkersininventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player pressed Escape with a panel open: close the top one (the item before the ender chest). */
public record CloseC2S() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseC2S> CODEC = StreamCodec.unit(new CloseC2S());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
