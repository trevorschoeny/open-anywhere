package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player double-clicked the item in menu slot {@code menuSlot}, or clicked it with the
 * open key held. {@code fromCursor}: the double-click's first click lifted the item onto
 * the cursor (vanilla's pickup), so the server puts it back in the slot first. A request:
 * the server checks everything again before opening ({@link ServerSessions#open}).
 */
public record OpenC2S(int menuSlot, boolean fromCursor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenC2S> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenC2S::menuSlot, ByteBufCodecs.BOOL, OpenC2S::fromCursor, OpenC2S::new).cast();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
