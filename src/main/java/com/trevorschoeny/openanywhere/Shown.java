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

    /** What the client knows: the item tab in front, and whether its slots are locked. */
    public interface ClientView {
        /** The item in front as the row last showed it, or empty. */
        ItemStack item();
        /** Whether {@code container} holds the slots of the item in front while it's off its place. */
        boolean locked(Container container);
    }

    private static volatile ClientView client = new ClientView() {
        @Override public ItemStack item() { return ItemStack.EMPTY; }
        @Override public boolean locked(Container container) { return false; }
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
     * True when {@code container} holds the slots of an item whose screen someone has in front
     * while the item is off its place (on the cursor): they're locked then. The slot lock asks
     * this for every operation, so it is a short scan. Containers are per side, so both views
     * can be asked.
     */
    static boolean locked(Container container) {
        return ServerTabs.locked(container) || client.locked(container);
    }
}
