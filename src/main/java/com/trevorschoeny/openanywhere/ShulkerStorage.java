package com.trevorschoeny.openanywhere;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * An opened shulker box's 27 stacks, as the container behind vanilla's shulker box menu
 * (wrapped in SlotKit's {@code StorageContainerAdapter}).
 *
 * <p>Live, not copied. Vanilla often changes a slot's stack in place and then only says
 * so ({@code itemstack.grow(n); slot.setChanged()} when stacking onto it), so reads hand
 * out the same stack objects each time and every change, in place or through
 * {@link #setStack}, is written back into the box's {@code minecraft:container}
 * component at once. The list is reloaded whenever the component is no longer the one
 * it was read from (the box changed by anything else).
 *
 * <p>Bound to the one box for the menu's life; the session's per-tick check closes the
 * menu if the box moves.
 */
final class ShulkerStorage implements Storage {

    private final ItemStack box;
    private ItemContainerContents loadedFrom = null;
    private final NonNullList<ItemStack> live = NonNullList.withSize(Openable.SHULKER.slots, ItemStack.EMPTY);

    ShulkerStorage(ItemStack box) {
        this.box = box;
    }

    private NonNullList<ItemStack> live() {
        ItemContainerContents contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != loadedFrom) {
            for (int i = 0; i < live.size(); i++) live.set(i, ItemStack.EMPTY);
            contents.copyInto(live);
            loadedFrom = contents;
        }
        return live;
    }

    @Override
    public ItemStack getStack(int i) {
        return live().get(i);
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        live().set(i, stack);
        markDirty();
    }

    @Override
    public int size() {
        return live.size();
    }

    @Override
    public void markDirty() {
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(live()));
        loadedFrom = box.get(DataComponents.CONTAINER);
    }
}
