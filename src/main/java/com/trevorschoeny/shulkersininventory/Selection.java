package com.trevorschoeny.shulkersininventory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * Which stack inside a hovered shulker box or ender chest each player has scrolled to,
 * for the tooltip view. Vanilla keeps a bundle's selection on the item itself; a shulker
 * can't do that without a new data component, which would lock vanilla clients out of a
 * server running this mod (design, "No lockouts"). So it lives here, in memory.
 *
 * <p>Both sides keep one: the client sets its own while scrolling and sends it
 * ({@link SelectC2S}), so the click's prediction on the client and the real click on the
 * server read the same selection. Keyed weakly by player, so a player that's gone
 * (respawned, left) takes its entry with it.
 */
public final class Selection {

    private Selection() {}

    /** A selected stack: index {@code index} inside the item in menu slot {@code slot}. */
    public record Entry(int slot, int index) {}

    // Client and server threads use different keys, but share the map in singleplayer.
    private static final Map<Player, Entry> SELECTED = Collections.synchronizedMap(new WeakHashMap<>());

    /** An index below 0 clears it (the client stopped hovering). */
    public static void set(Player player, int slot, int index) {
        if (index < 0) SELECTED.remove(player);
        else SELECTED.put(player, new Entry(slot, index));
    }

    public static Entry get(Player player) {
        return SELECTED.get(player);
    }

    public static void clear(Player player) {
        SELECTED.remove(player);
    }
}
