package com.trevorschoeny.shulkersininventory;

import java.util.Map;
import java.util.WeakHashMap;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

/**
 * The bundle panel on the server: one slot per stack the opened bundle holds, in the
 * bundle's own order, then the slot that adds one. Live like {@link ShulkerStorage}:
 * every change is written straight back into the bundle's {@code minecraft:bundle_contents}.
 *
 * <p>Each bundle slot is its own one-slot SlotKit group, so each can be shown or hidden
 * on its own and the panel grows as the bundle fills ({@link Panels}). They all read one
 * list per player, this class; {@link #slot} is the view one group gets.
 *
 * <p>Writing back drops the emptied slots, so taking a stack from the middle moves the
 * ones after it up by one, as in the bundle itself, and vanilla's slot sync shows the
 * shift. What may go in, and how much, is each slot's gate ({@link Panels}), checked
 * before an item moves, never after.
 */
final class BundleStorage {

    // Server players only, so one entity id is one player.
    private static final Map<Player, BundleStorage> BY_PLAYER = new WeakHashMap<>();

    static synchronized BundleStorage of(Player player) {
        return BY_PLAYER.computeIfAbsent(player, BundleStorage::new);
    }

    private final Player player;
    private ItemStack loadedBundle = ItemStack.EMPTY;
    private BundleContents loadedFrom = null;
    private final NonNullList<ItemStack> live = NonNullList.withSize(Openable.BUNDLE.slots, ItemStack.EMPTY);

    private BundleStorage(Player player) {
        this.player = player;
    }

    /** Bundle slot {@code index} as a one-slot storage. */
    Storage slot(int index) {
        return new Storage() {
            @Override public ItemStack getStack(int i) { return get(index); }
            @Override public void setStack(int i, ItemStack stack) { set(index, stack); }
            @Override public int size() { return 1; }
            @Override public void markDirty() { writeBackIfOpen(); }
        };
    }

    private ItemStack bundle() {
        Session session = Sessions.get(player, Openable.BUNDLE);
        if (session == null) return null;
        ItemStack bundle = session.stack();
        BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        if (bundle != loadedBundle || contents != loadedFrom) {
            for (int i = 0; i < live.size(); i++) live.set(i, ItemStack.EMPTY);
            int i = 0;
            for (ItemStack stack : (Iterable<ItemStack>) contents.itemCopies()::iterator) live.set(i++, stack);
            loadedBundle = bundle;
            loadedFrom = contents;
        }
        return bundle;
    }

    private ItemStack get(int index) {
        return bundle() == null ? ItemStack.EMPTY : live.get(index);
    }

    private void set(int index, ItemStack stack) {
        ItemStack bundle = bundle();
        if (bundle == null) return;
        live.set(index, stack);
        writeBack(bundle);
    }

    private void writeBackIfOpen() {
        ItemStack bundle = bundle();
        if (bundle != null) writeBack(bundle);
    }

    /**
     * Writes the panel's stacks into the bundle in the panel's order, so a stack stays
     * where it was put. Not {@code BundleContents.copyWithContents}: that re-inserts
     * every stack through vanilla's {@code tryInsert}, which adds each one at the front,
     * reversing the bundle on every write (found in game, 2026-10-16).
     */
    private void writeBack(ItemStack bundle) {
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(live.stream()
                .filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList()));
        // Reloaded on the next read, which also packs the emptied slots out.
        loadedFrom = null;
    }
}
