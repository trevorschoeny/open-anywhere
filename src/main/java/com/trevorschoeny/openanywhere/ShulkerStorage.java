package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * An opened shulker box's stacks, as the storage behind its screen's container.
 *
 * <p>Live, not copied. Vanilla often changes a slot's stack in place and then only says
 * so ({@code itemstack.grow(n); slot.setChanged()} when stacking onto it), so reads hand
 * out the same stack objects each time and every change, in place or through
 * {@link #setStack}, is written back into the box's {@code minecraft:container}
 * component at once. The list is reloaded whenever the component is no longer the one
 * it was read from (the box changed by anything else).
 *
 * <p>Its size is the box's room: vanilla's 27, or more when another mod gives the box more
 * ({@link com.trevorschoeny.openanywhere.api.OpenItems#boxSize}). Whatever the box holds past
 * its room is kept as it is and written back with the rest, so nothing in the box is ever
 * lost by opening it here.
 */
final class ShulkerStorage implements Storage {

    private final ItemStack box;
    private final int size;
    private ItemContainerContents loadedFrom = null;
    // The box's whole list: the room's slots, then anything stored past them.
    private final List<ItemStack> live = new ArrayList<>();

    ShulkerStorage(ItemStack box, int size) {
        this.box = box;
        this.size = size;
    }

    private List<ItemStack> live() {
        ItemContainerContents contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != loadedFrom) {
            live.clear();
            contents.itemCopies().forEach(live::add);
            while (live.size() < size) live.add(ItemStack.EMPTY);
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
        return size;
    }

    @Override
    public void markDirty() {
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(live()));
        loadedFrom = box.get(DataComponents.CONTAINER);
    }
}
