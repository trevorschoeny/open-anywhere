package com.trevorschoeny.openanywhere;


import com.trevlar.slotkit.api.slot.OnDemandGroup;
import com.trevlar.slotkit.api.storage.Storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

/**
 * An open bundle's slots on the server: one slot per stack it holds, in the
 * bundle's own order, then the slot that adds one. Live like {@link ShulkerStorage}:
 * every change is written straight back into the bundle's {@code minecraft:bundle_contents},
 * and the group's live count follows, so the add slot appears and moves as things go
 * in ({@link Groups#liveCount}).
 *
 * <p>Writing back drops the emptied slots, so taking a stack from the middle moves the
 * ones after it up by one, as in the bundle itself, and vanilla's slot sync shows the
 * shift. What may go in, and how much, is each slot's gate ({@link Groups}), checked
 * before an item moves, never after.
 */
final class BundleStorage implements Storage {

    private final ItemStack bundle;
    private BundleContents loadedFrom = null;
    private final NonNullList<ItemStack> live = NonNullList.withSize(Openable.BUNDLE.slots, ItemStack.EMPTY);
    // The slots it shows in, whose live count follows the bundle. Set right after they're added.
    private OnDemandGroup.@Nullable Instance shownIn;

    BundleStorage(ItemStack bundle) {
        this.bundle = bundle;
    }

    /** The slots this storage shows in, now added: sized to the bundle from here on. */
    void shownIn(OnDemandGroup.Instance instance) {
        shownIn = instance;
        instance.setActiveCount(liveCount(contents(bundle)));
    }

    private static BundleContents contents(ItemStack bundle) {
        return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    /** How many of the bundle's slots are live: its stacks, plus the add slot unless it's full. */
    static int liveCount(BundleContents contents) {
        return BundleView.addSlot(contents) >= 0 ? contents.size() + 1 : contents.size();
    }

    private ItemStack bundle() {
        BundleContents contents = contents(bundle);
        if (contents != loadedFrom) {
            for (int i = 0; i < live.size(); i++) live.set(i, ItemStack.EMPTY);
            int i = 0;
            for (ItemStack stack : (Iterable<ItemStack>) contents.itemCopies()::iterator) live.set(i++, stack);
            loadedFrom = contents;
        }
        return bundle;
    }

    @Override
    public ItemStack getStack(int i) {
        return bundle() == null ? ItemStack.EMPTY : live.get(i);
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        ItemStack bundle = bundle();
        if (bundle == null) return;
        live.set(i, stack);
        writeBack(bundle);
    }

    @Override
    public int size() {
        return live.size();
    }

    @Override
    public void markDirty() {
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
        // An item the bundle already holds stacks onto it (Trev, 2026-10-22): each stack
        // folds into the first earlier stack of the same item, up to a full stack, as
        // vanilla's own bundle does. The rest, if any, stays where it was put.
        for (int i = 1; i < live.size(); i++) {
            ItemStack moving = live.get(i);
            for (int j = 0; j < i && !moving.isEmpty(); j++) {
                ItemStack into = live.get(j);
                if (into.isEmpty() || !ItemStack.isSameItemSameComponents(into, moving)) continue;
                int room = into.getMaxStackSize() - into.getCount();
                if (room <= 0) continue;
                int n = Math.min(room, moving.getCount());
                into.grow(n);
                moving.shrink(n);
            }
        }
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(live.stream()
                .filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList()));
        // Reloaded on the next read, which also packs the emptied slots out.
        loadedFrom = null;
        if (shownIn != null) shownIn.setActiveCount(liveCount(contents(bundle)));
    }
}
