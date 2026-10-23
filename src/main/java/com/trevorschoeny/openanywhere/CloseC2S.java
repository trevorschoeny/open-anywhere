package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player clicked the close button on the tab of {@code kind}'s lane {@code lane}. */
public record CloseC2S(Openable kind, int lane) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseC2S> CODEC = StreamCodec.composite(
            Openable.STREAM_CODEC, CloseC2S::kind,
            ByteBufCodecs.VAR_INT, CloseC2S::lane,
            CloseC2S::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
