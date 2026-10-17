package com.trevorschoeny.shulkersininventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * One opened item: what it is, the menu it was opened on, and where it sits.
 *
 * <p>The session is tied to the item, not to its slot: {@code stack} is the exact
 * stack object that was opened. Every open-from-inventory mod in the research shipped
 * dupes because its session followed a slot or a hand, so whatever moved or copied the
 * item forked its contents. Here the server checks every tick that this very stack is
 * still at {@code container}/{@code containerSlot} on {@code menu}, and ends the session
 * the moment it isn't ({@link ServerSessions#tick}).
 *
 * @param container     the container holding the opened item (the player's inventory,
 *                      a chest, or this mod's ender chest panel)
 * @param containerSlot its index in that container
 * @param menuSlot      its index in the menu, which is what the two sides share
 */
public record Session(Openable kind, AbstractContainerMenu menu, Container container, int containerSlot,
                      int menuSlot, ItemStack stack) {

    /** True while the opened stack is still exactly where it was opened. */
    public boolean stillInPlace() {
        return container.getItem(containerSlot) == stack;
    }
}
