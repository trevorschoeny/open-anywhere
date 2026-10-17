package com.trevorschoeny.shulkersininventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player shift+right-clicked the item in menu slot {@code menuSlot}. A request:
 * the server checks everything again before opening ({@link ServerSessions#open}).
 */
public record OpenC2S(int menuSlot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenC2S> CODEC =
            ByteBufCodecs.VAR_INT.map(OpenC2S::new, OpenC2S::menuSlot).cast();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
