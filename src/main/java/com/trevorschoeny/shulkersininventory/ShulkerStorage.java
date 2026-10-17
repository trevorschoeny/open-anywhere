package com.trevorschoeny.shulkersininventory;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The shulker panel's slots on the server: the opened box's 27 stacks.
 *
 * <p>Live, not copied. Vanilla often changes a slot's stack in place and then only says
 * so ({@code itemstack.grow(n); slot.setChanged()} when stacking onto it), so reads hand
 * out the same stack objects each time and every change, in place or through
 * {@link #setStack}, is written back into the box's {@code minecraft:container}
 * component at once. The list is reloaded whenever the box or its component is no
 * longer the one it was read from (another box opened, or the box changed by anything
 * else).
 *
 * <p>With nothing open it holds nothing and takes nothing.
 */
final class ShulkerStorage implements Storage {

    private final Player player;
    private ItemStack loadedBox = ItemStack.EMPTY;
    private ItemContainerContents loadedFrom = null;
    private final NonNullList<ItemStack> live = NonNullList.withSize(Openable.SHULKER.slots, ItemStack.EMPTY);

    ShulkerStorage(Player player) {
        this.player = player;
    }

    /** The opened box, with {@link #live} matching its contents; or null with nothing open. */
    private ItemStack box() {
        Session session = Sessions.get(player, Openable.SHULKER);
        if (session == null) return null;
        ItemStack box = session.stack();
        ItemContainerContents contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (box != loadedBox || contents != loadedFrom) {
            for (int i = 0; i < live.size(); i++) live.set(i, ItemStack.EMPTY);
            contents.copyInto(live);
            loadedBox = box;
            loadedFrom = contents;
        }
        return box;
    }

    @Override
    public ItemStack getStack(int i) {
        return box() == null ? ItemStack.EMPTY : live.get(i);
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        ItemStack box = box();
        if (box == null) return;
        live.set(i, stack);
        writeBack(box);
    }

    @Override
    public int size() {
        return live.size();
    }

    @Override
    public void markDirty() {
        ItemStack box = box();
        if (box != null) writeBack(box);
    }

    private void writeBack(ItemStack box) {
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(live));
        loadedFrom = box.get(DataComponents.CONTAINER);
    }
}
