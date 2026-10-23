package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The opened item whose screen is in front, as each side knows it, for the code both sides
 * run (the slot lock, the bundle's slot rules). The server's answer is the truth
 * ({@link ServerTabs}); the client's is what the server last told it, set by the client.
 */
public final class Shown {

    private Shown() {}

    /** What the client knows: the item tab in front, and the slot holding it. */
    public interface ClientView {
        /** The item in front as the row last showed it, or empty. */
        ItemStack item();
        /** Whether {@code container}'s slot {@code slot} holds the item in front. */
        boolean holds(Container container, int slot);
    }

    private static volatile ClientView client = new ClientView() {
        @Override public ItemStack item() { return ItemStack.EMPTY; }
        @Override public boolean holds(Container container, int slot) { return false; }
    };

    /** The client sets its view at init. */
    public static void client(ClientView view) {
        client = view;
    }

    /** The item {@code player} has in front, this side's view: on the server the live item itself. Empty for none. */
    public static ItemStack item(@Nullable Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.level().isClientSide()) return client.item();
        return player instanceof ServerPlayer sp ? ServerTabs.frontItem(sp) : ItemStack.EMPTY;
    }

    /**
     * True when {@code container}'s slot {@code slot} holds an item whose screen someone has in
     * front. The slot lock asks this for every operation, so it is a short scan. Containers
     * are per side, so both views can be asked.
     */
    static boolean locked(Container container, int slot) {
        return ServerTabs.holds(container, slot) || client.holds(container, slot);
    }
}
