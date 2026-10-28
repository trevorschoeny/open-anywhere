package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A double-click's first click is pending ({@code on}), or its window ended with no second
 * click (off). Sent ahead of the first click itself, so a box lifted from in front keeps its
 * tab until the double-click is decided, as any lifted item stays drawn in its slot till then.
 */
public record HoldC2S(boolean on) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HoldC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "hold"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoldC2S> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, HoldC2S::on, HoldC2S::new).cast();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
