package com.trevorschoeny.shulkersininventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The client scrolled to stack {@code index} inside the shulker box or ender chest in
 * menu slot {@code slot}; -1 clears it. Vanilla's bundle equivalent is
 * {@code ServerboundSelectBundleItemPacket}. Its type doubles as the "server runs this
 * mod" check: Fabric only lets the client send it when the server registered it.
 */
public record SelectC2S(int slot, int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectC2S::slot,
            ByteBufCodecs.VAR_INT, SelectC2S::index,
            SelectC2S::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
