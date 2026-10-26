package com.trevorschoeny.openanywhere;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One tab as the server tells the client: which it is, the item it stands for (its icon,
 * and the item itself: its name, and a bundle's contents for the bundle's slot rules), the
 * container it sits in, and whether it's a sub-tab under that container's tab or a main tab.
 * The inventory and world tabs carry no item and no parent; the client draws those itself.
 *
 * @param parent the tab of the container the item sits in; null for a top-level tab: the
 *               inventory, the world container, and Pocket Ender Chest's ender chest while open
 * @param sub    true for a closed box under its container's tab; false for a main tab (an open one)
 */
public record Tab(TabId id, ItemStack item, @Nullable TabId parent, boolean sub) {

    public static Tab of(TabId id) {
        return new Tab(id, ItemStack.EMPTY, null, false);
    }

    /** The same tab, showing the same item, in the same place: what the server compares to send only changes. */
    boolean same(Tab other) {
        return id.equals(other.id) && ItemStack.matches(item, other.item)
                && java.util.Objects.equals(parent, other.parent) && sub == other.sub;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
            TabId.STREAM_CODEC.cast(), Tab::id,
            ItemStack.OPTIONAL_STREAM_CODEC, Tab::item,
            ByteBufCodecs.optional(TabId.STREAM_CODEC).cast(), t -> Optional.ofNullable(t.parent()),
            ByteBufCodecs.BOOL, Tab::sub,
            (id, item, parent, sub) -> new Tab(id, item, parent.orElse(null), sub));
}
