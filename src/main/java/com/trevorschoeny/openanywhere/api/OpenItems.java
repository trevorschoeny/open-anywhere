package com.trevorschoeny.openanywhere.api;

import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Shown;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The item a player has open through Open Anywhere, for other mods. Every opened item is a
 * screen of its own (a shulker box on vanilla's shulker box screen, the ender chest on
 * vanilla's ender chest screen, a bundle on Open Anywhere's), so "the open item" is the one
 * whose screen is in front: the player's open menu is that item's.
 *
 * <p>Both sides. On the server it is the item itself, live: change it in place
 * ({@code stack.set(...)}) and the change is kept, written back up to the slot it's in.
 * Putting a copy in its slot instead moves it, and its screen closes. On the client it is a
 * copy, as the server last showed it, for reading.
 */
public final class OpenItems {

    private OpenItems() {}

    /**
     * One opened item.
     *
     * @param kind  a shulker box, the ender chest or a bundle
     * @param stack the item: live on the server, a copy on the client
     */
    public record OpenItem(Openable kind, ItemStack stack) {}

    /**
     * The item whose screen {@code player} has in front, or null when their screen is
     * something else (the inventory, a chest, any other). Check this on the server before
     * acting on a request about the open item, as for an item in the hand.
     */
    public static @Nullable OpenItem shown(Player player) {
        ItemStack stack = Shown.item(player);
        Openable kind = Openable.of(stack);
        return kind == null ? null : new OpenItem(kind, stack);
    }
}
