package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player clicked tab {@code id} (or turned the wheel onto it). The server checks it's on the row. */
public record PickC2S(TabId id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PickC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickC2S> CODEC =
            StreamCodec.composite(TabId.STREAM_CODEC.cast(), PickC2S::id, PickC2S::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
