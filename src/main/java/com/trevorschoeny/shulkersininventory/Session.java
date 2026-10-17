package com.trevorschoeny.shulkersininventory;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * One opened item: what it is, the menu it opened, and where it sits.
 *
 * <p>The session is tied to the item, not to its slot: {@code stack} is the exact stack
 * object that was opened. Every open-from-inventory mod in the research shipped dupes
 * because its session followed a slot or a hand, so whatever moved or copied the item
 * forked its contents. Here the server checks every tick that this very stack is still
 * at {@code container}/{@code containerSlot} with {@code menu} open, and ends the
 * session the moment it isn't ({@link ServerSessions#tick}).
 *
 * @param menu          the menu the item opened: vanilla's shulker box or ender chest
 *                      menu, or (for the bundle) the menu its panel sits on
 * @param container     the container holding the opened item (the player's inventory,
 *                      or the ender chest for a shulker kept there); null on the client
 *                      when that container isn't on the open screen
 * @param containerSlot its index in that container
 * @param menuSlot      its index in {@code menu}, or -1 when it isn't on that menu
 * @param parent        the session to step back to on close (the ender chest a shulker
 *                      was opened from), or null to step back to the inventory
 */
public record Session(Openable kind, AbstractContainerMenu menu, @Nullable Container container, int containerSlot,
                      int menuSlot, ItemStack stack, @Nullable Session parent) {

    /** True while the opened stack is still exactly where it was opened. */
    public boolean stillInPlace() {
        return container != null && container.getItem(containerSlot) == stack;
    }
}
