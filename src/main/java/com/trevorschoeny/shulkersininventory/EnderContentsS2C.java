package com.trevorschoeny.shulkersininventory;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The player's whole ender chest, sent whenever it changes (and on join), so the client
 * can show it in the tooltip view without a real ender chest being opened. Only sent to
 * clients that run this mod.
 */
public record EnderContentsS2C(List<ItemStack> items) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EnderContentsS2C> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "ender_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnderContentsS2C> CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(EnderContentsS2C::new, EnderContentsS2C::items);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
