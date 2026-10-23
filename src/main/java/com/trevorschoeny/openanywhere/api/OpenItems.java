package com.trevorschoeny.openanywhere.api;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Session;
import com.trevorschoeny.openanywhere.Sessions;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a player has open through Open Anywhere, for other mods. Both sides: the server's
 * answer is the truth; the client's mirrors what the server told it.
 *
 * <p>An opened item is named by its {@link OpenItem#menuSlot()}: its slot on the player's
 * open menu, whatever holds it (the inventory, a chest, the ender chest's section). That
 * is the number to send in a packet; the server checks it with {@link #openAt}.
 *
 * <p>The item stays the same stack object for as long as it is open, and it is open only
 * while that object is still in its slot. So change it in place ({@code stack.set(...)}):
 * putting a copy in the slot closes it. Writing the contents back touches only
 * {@code minecraft:container} (a box) or {@code minecraft:bundle_contents} (a bundle),
 * on that same stack, so every other component stays as it is.
 */
public final class OpenItems {

    private OpenItems() {}

    /**
     * One opened item.
     *
     * @param kind          a shulker box, the ender chest or a bundle
     * @param lane          its number among the open items of its kind
     * @param menuSlot      its slot on the menu it opened on, the player's open menu
     * @param container     what holds it; null on the client when that isn't on the screen
     * @param containerSlot its index in {@code container}
     */
    public record OpenItem(Openable kind, int lane, int menuSlot, @Nullable Container container, int containerSlot) {

        /** The item as it is now, read from its slot; empty when the container isn't known. */
        public ItemStack stack() {
            return container == null ? ItemStack.EMPTY : container.getItem(containerSlot);
        }

        /** Its index in the player's inventory, or -1 when it isn't in the inventory. */
        public int inventorySlot() {
            return container instanceof Inventory ? containerSlot : -1;
        }

        /** The slot group showing its contents on the menu: the box's slots, in their order. */
        public SlotGroupId group() {
            return Groups.id(kind, lane);
        }
    }

    /** Everything {@code player} has open on this side, oldest first. */
    public static List<OpenItem> of(Player player) {
        List<OpenItem> open = new ArrayList<>();
        for (Session s : Sessions.of(player)) {
            if (s.menu() == player.containerMenu) open.add(new OpenItem(s.kind(), s.lane(), s.menuSlot(), s.container(), s.containerSlot()));
        }
        return open;
    }

    /**
     * On the server: the item {@code player} has open at {@code menuSlot} of their open menu,
     * the very stack to change, or null when nothing is open there. Check this before acting on
     * a request about an opened item, as for an item in the hand.
     */
    public static @Nullable ItemStack openAt(Player player, int menuSlot) {
        for (Session s : Sessions.of(player)) {
            if (s.menuSlot() == menuSlot && s.menu() == player.containerMenu && s.stillInPlace()) return s.stack();
        }
        return null;
    }
}
