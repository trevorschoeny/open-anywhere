package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One tab as the server tells the client: which it is, the item it stands for (its icon,
 * and the item itself: its name, and a bundle's contents for the bundle's slot rules).
 * The inventory and world tabs carry no item; the client draws those itself.
 */
public record Tab(TabId id, ItemStack item) {

    public static Tab of(TabId id) {
        return new Tab(id, ItemStack.EMPTY);
    }

    /** The same tab, showing the same item: what the server compares to send only changes. */
    boolean same(Tab other) {
        return id.equals(other.id) && ItemStack.matches(item, other.item);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
            TabId.STREAM_CODEC.cast(), Tab::id,
            ItemStack.OPTIONAL_STREAM_CODEC, Tab::item,
            Tab::new);
}
