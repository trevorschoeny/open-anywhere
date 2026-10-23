package com.trevorschoeny.openanywhere.api;

import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Shown;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

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
     * The container behind an opened shulker box's screen ({@code ShulkerBoxMenu}'s), on the
     * server: the box item's own slots, written straight into it. A mod that knows a box's
     * container by its block entity reads the box item from here instead.
     */
    public interface Box {
        /** The box item itself, live. */
        ItemStack stack();
    }

    private static final List<ToIntFunction<ItemStack>> BOX_SIZES = new CopyOnWriteArrayList<>();

    /**
     * For a mod that gives shulker boxes more slots than vanilla's 27: {@code size} says how
     * many slots a box item has (anything up to vanilla's limit for a container item, 256), or
     * 27 or less for vanilla's. An opened box's container ({@link Box}) is as big as the
     * largest answer. Vanilla's shulker box menu shows only the first 27; the mod adds the
     * slots for the rest to the menu, and the shift-click that knows them. Common init.
     *
     * <p>Whatever a box holds past its size is kept untouched, so a box is never emptied by
     * opening it here, whether or not its mod is installed.
     */
    public static void boxSize(ToIntFunction<ItemStack> size) {
        BOX_SIZES.add(size);
    }

    /** How many slots the box item {@code box} has: 27, or more where a mod says so ({@link #boxSize}). */
    public static int boxSizeOf(ItemStack box) {
        int size = 27;
        for (ToIntFunction<ItemStack> f : BOX_SIZES) size = Math.max(size, Math.min(256, f.applyAsInt(box)));
        return size;
    }

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
