package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * One opened item: what it is, the menu it opened on, where it sits, and its lane.
 *
 * <p>The session is tied to the item, not to its slot: {@code stack} is the exact stack
 * object that was opened. Every open-from-inventory mod we looked at shipped dupes
 * because its session followed a slot or a hand, so whatever moved or copied the item
 * forked its contents. Here the server checks every tick that this very stack is still
 * at {@code container}/{@code containerSlot} with {@code menu} open, and ends the
 * session the moment it isn't ({@link ServerSessions#tick}).
 *
 * @param lane          its slot group's number among its kind's on {@code menu}
 *                      ({@link Groups}); its tab is keyed by it
 * @param menu          the menu it opened on: a chest's, or the host's ({@link ContainerHost})
 * @param container     the container holding the opened item (the player's inventory, a
 *                      chest, or the ender chest's group); null on the client when that
 *                      container isn't on the open screen
 * @param containerSlot its index in that container
 * @param menuSlot      its index in {@code menu}, or -1 when it isn't on that menu
 */
public record Session(Openable kind, int lane, AbstractContainerMenu menu, @Nullable Container container,
                      int containerSlot, int menuSlot, ItemStack stack) {

    /** True for the same opened thing: the same kind in the same lane. */
    public boolean sameSlotAs(Openable kind, int lane) {
        return this.kind == kind && this.lane == lane;
    }

    /** True while the opened stack is still exactly where it was opened. */
    public boolean stillInPlace() {
        return container != null && container.getItem(containerSlot) == stack;
    }
}
