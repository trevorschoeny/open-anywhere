package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player clicked the close button on item tab {@code id}. */
public record CloseC2S(TabId id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseC2S> CODEC =
            StreamCodec.composite(TabId.STREAM_CODEC.cast(), CloseC2S::id, CloseC2S::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
