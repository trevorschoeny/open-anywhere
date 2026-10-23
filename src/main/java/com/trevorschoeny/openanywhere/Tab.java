package com.trevorschoeny.openanywhere;

import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One tab as the server tells the client: which it is, the item it stands for (its icon,
 * and the item itself: its name, and a bundle's contents for the bundle's slot rules),
 * and, for an item inside another, that item's name for the tooltip's "In ..." line.
 * The inventory and world tabs carry no item; the client draws those itself.
 */
public record Tab(TabId id, ItemStack item, Optional<Component> in) {

    public static Tab of(TabId id) {
        return new Tab(id, ItemStack.EMPTY, Optional.empty());
    }

    /** The same tab, showing the same item: what the server compares to send only changes. */
    boolean same(Tab other) {
        return id.equals(other.id) && in.equals(other.in) && ItemStack.matches(item, other.item);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
            TabId.STREAM_CODEC.cast(), Tab::id,
            ItemStack.OPTIONAL_STREAM_CODEC, Tab::item,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), Tab::in,
            Tab::new);
}
