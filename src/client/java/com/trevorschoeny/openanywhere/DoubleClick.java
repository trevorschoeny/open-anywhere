package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A plain left double-click on an openable item opens it; on an item
 * that's open, it closes it. Shift+double-click stays vanilla's "move all".
 *
 * <p>The first click is vanilla's: it lifts the item onto the cursor (an open item's slot
 * is locked, so it stays put). The second, on the same slot within {@link #WINDOW_MS} and
 * with the cursor not moved, would be vanilla's "gather all of this item"; it is taken
 * instead and sent as an open, the item going back in its slot first. A stack of boxes
 * (Carpet) never opens, so its double-click stays vanilla's gather; a stack of ender chests
 * opens the ender chest, giving up gather for ender chests.
 *
 * <p>Between the clicks the lifted item is drawn where it sat ({@link #heldAt}), so a
 * double-click shows no movement; moving the cursor ends the window, and the item goes to
 * the cursor as any picked-up item does.
 */
public final class DoubleClick {

    private DoubleClick() {}

    /** The window: one second, as long as the cursor stays put. */
    private static final long WINDOW_MS = 1000;

    // The first click worth pairing: its slot (-1: none), when, where the mouse was, and
    // the slot's place on screen, where the lifted item is drawn meanwhile.
    // The slot is named by its place in the player's inventory where it's one of those, so a
    // second click still pairs after the first one changed the screen (a box lifted from
    // under its own screen goes back to the inventory's), else by its index on the menu.
    private static int lastSlot = -1;
    private static long lastClick;
    private static double firstMouseX, firstMouseY;
    private static int heldX, heldY;

    /**
     * Where the item on the cursor is drawn: its slot's place while a double-click may still
     * come (just picked up, the window open, the cursor still), else {@code null}, the cursor.
     */
    public static int @Nullable [] heldAt(int mouseX, int mouseY) {
        if (lastSlot < 0) return null;
        // The screen draws at whole pixels; the click came in at fractions of one.
        if (Util.getMillis() - lastClick >= WINDOW_MS
                || mouseX != (int) Math.floor(firstMouseX) || mouseY != (int) Math.floor(firstMouseY)) {
            lastSlot = -1;
            return null;
        }
        return new int[] {heldX, heldY};
    }

    /** A click on {@code hovered}, before the screen sees it. True when it was taken. */
    static boolean click(Screen screen, MouseButtonEvent event, Slot hovered, ItemStack carried, Player player) {
        // Creative's inventory tab shows the player's slots on a menu of its own; the server
        // has the player's real inventory menu open, so the slot is named by its index there.
        // Creative's other tabs aren't the inventory: nothing opens there.
        boolean creative = screen instanceof CreativeModeInventoryScreen;
        if (creative && !((CreativeModeInventoryScreen) screen).isInventoryOpen()) return false;
        if (!Settings.doubleClick()) return false;
        int target = creative ? OpenAnywhereClient.realIndex(player, hovered) : hovered.index;
        if (target < 0) return false;
        int place = hovered.container instanceof net.minecraft.world.entity.player.Inventory ? 1000 + hovered.getContainerSlot() : target;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.hasShiftDown()) {
            lastSlot = -1;
            return false;
        }
        long now = Util.getMillis();
        boolean second = place == lastSlot && now - lastClick < WINDOW_MS
                // The same pixel, not the same fraction of one: a screen opened in place of
                // another between the clicks puts the cursor back where it was, to the pixel.
                && Math.floor(event.x()) == Math.floor(firstMouseX) && Math.floor(event.y()) == Math.floor(firstMouseY);
        if (!second) {
            // A first click worth pairing: on an openable item, or an open one.
            lastSlot = OpenAnywhereClient.opensOrIsOpen(player, hovered) && carried.isEmpty() ? place : -1;
            lastClick = now;
            firstMouseX = event.x();
            firstMouseY = event.y();
            ScreenAccessor edge = (ScreenAccessor) screen;
            heldX = edge.openanywhere$leftPos() + hovered.x;
            heldY = edge.openanywhere$topPos() + hovered.y;
            return false;
        }
        lastSlot = -1; // a third click starts over
        if (!hovered.hasItem() && opens(carried)) {
            if (creative) {
                // Creative keeps the cursor on the client and tells the server each slot's
                // contents itself, so the item goes back here, the way creative's own click
                // puts it (a creative slot update), then opens as if never lifted: as if the
                // player had opened the container itself, so its screen replaces creative's.
                player.inventoryMenu.getSlot(target).set(carried);
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                ClientPlayNetworking.send(new OpenC2S(target, false));
            } else {
                // Back in its slot now, on this side, rather than once the server says so:
                // the lift lasts only as long as the player's own double-click. The server
                // puts it back too, and its sync corrects this if it refuses.
                hovered.set(carried);
                player.containerMenu.setCarried(ItemStack.EMPTY);
                ClientPlayNetworking.send(new OpenC2S(hovered.index, true));
            }
            return true;
        }
        if (carried.isEmpty() && OpenAnywhereClient.isOpen(player, hovered)) {
            ClientPlayNetworking.send(new OpenC2S(hovered.index, false));
            return true;
        }
        return false;
    }

    /** An item a double-click opens: one box or bundle, or any stack of ender chests. */
    private static boolean opens(ItemStack stack) {
        Openable kind = Openable.of(stack);
        return kind == Openable.ENDER_CHEST || kind != null && stack.getCount() == 1;
    }
}
