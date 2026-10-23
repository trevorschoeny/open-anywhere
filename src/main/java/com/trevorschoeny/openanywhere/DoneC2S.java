package com.trevorschoeny.openanywhere;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player left the screens for the world (Escape, the inventory key): what they had open
 * closes. The server can't tell the inventory screen from no screen, so the client says.
 */
public record DoneC2S() implements CustomPacketPayload {

    public static final DoneC2S INSTANCE = new DoneC2S();

    public static final CustomPacketPayload.Type<DoneC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "done"));

    public static final StreamCodec<ByteBuf, DoneC2S> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
