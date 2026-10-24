package com.trevorschoeny.openanywhere;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player's tab row, whole, in order, which tab's screen is in front, and whether that
 * item is off its place (on the cursor), its slots locked and greyed. Sent when
 * either changes ({@link ServerTabs#tick}); one tab alone (the inventory) means no row.
 */
public record TabsS2C(List<Tab> row, TabId shown, boolean frozen) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TabsS2C> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "tabs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TabsS2C> CODEC = StreamCodec.composite(
            Tab.STREAM_CODEC.apply(ByteBufCodecs.list()), TabsS2C::row,
            TabId.STREAM_CODEC.cast(), TabsS2C::shown,
            ByteBufCodecs.BOOL, TabsS2C::frozen,
            TabsS2C::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
