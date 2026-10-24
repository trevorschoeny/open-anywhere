package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The tab row on the client: what the server last sent ({@link TabsS2C}), and the clicks
 * that ask it to switch. Every tab is a real screen, so a click is a request: the server
 * switches the screen, and the row follows. The tab clicked draws selected at once.
 *
 * <p>Also how a slot on screen maps to an item on the row, for the open look in the slot
 * ({@link ClientMarks}) and the lock on the item in front's own slot.
 */
public final class Tabs {

    private Tabs() {}

    private static List<Tab> row = List.of();
    private static TabId shown = TabId.INVENTORY;
    // The tab just clicked, drawn selected until the server's answer comes.
    private static @Nullable TabId clicked;

    /** The server's row. */
    static void receive(TabsS2C payload) {
        row = payload.row();
        shown = payload.shown();
        clicked = null;
    }

    /** Back in the world: the row is gone with the screens. */
    static void clear() {
        row = List.of();
        shown = TabId.INVENTORY;
        clicked = null;
    }

    /** Whether {@code screen} is one the row stands on: the inventory, a chest, a shulker box, the bundle's host. */
    static boolean isTabScreen(@Nullable Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen;
    }

    /** Whether the row shows: on a tab screen, with something besides the inventory on it. */
    static boolean showing() {
        return row.size() > 1 && isTabScreen(Minecraft.getInstance().gui.screen());
    }

    /** The whole row, in order, while it shows. */
    public static List<Tab> row() {
        return showing() ? row : List.of();
    }

    /** The item tabs, in order, while the row shows: MenuKit's keys for them. */
    public static List<TabId> items() {
        List<TabId> ids = new ArrayList<>();
        for (Tab tab : row()) if (tab.id().type() == TabId.Type.ITEM) ids.add(tab.id());
        return ids;
    }

    /**
     * The row's groups: the inventory, the world container and the ender chest, each a header
     * tab with the items it holds as sub-tabs. An item's group is its top-level container's,
     * however deep it sits: a bundle in a box in the chest is the chest's.
     */
    public enum Group { INVENTORY, WORLD, ENDER_CHEST }

    /** The ender chest's tab, the ender chest group's header, or null with no ender chest in reach. */
    static @Nullable TabId enderHeader() {
        for (Tab tab : row()) if (tab.id().type() == TabId.Type.ITEM && Openable.of(tab.item()) == Openable.ENDER_CHEST) return tab.id();
        return null;
    }

    /** {@code group}'s header tab, or null while it isn't on the row. */
    public static @Nullable TabId header(Group group) {
        return switch (group) {
            case INVENTORY -> showing() ? TabId.INVENTORY : null;
            case WORLD -> hasWorld() ? TabId.WORLD : null;
            case ENDER_CHEST -> enderHeader();
        };
    }

    /** The group {@code id} is in, as its header or a sub-tab. */
    public static Group groupOf(TabId id) {
        if (id.type() == TabId.Type.INVENTORY) return Group.INVENTORY;
        if (id.type() == TabId.Type.WORLD || id.path() == null) return Group.WORLD;
        if (id.equals(enderHeader())) return Group.ENDER_CHEST;
        return switch (id.path().source()) {
            case INVENTORY -> Group.INVENTORY;
            case CONTAINER -> Group.WORLD;
            // At a placed ender chest the world container is the ender chest, and its items are its.
            case ENDER_CHEST -> enderHeader() != null ? Group.ENDER_CHEST : Group.WORLD;
        };
    }

    /** {@code group}'s sub-tabs, in the row's order: MenuKit's keys for them. */
    public static List<TabId> children(Group group) {
        List<TabId> ids = new ArrayList<>();
        TabId ender = enderHeader();
        for (TabId id : items()) if (!id.equals(ender) && groupOf(id) == group) ids.add(id);
        return ids;
    }

    /** Whether {@code group}'s sub-tabs are out: it holds the selected tab, or every group stays open. */
    public static boolean expanded(Group group) {
        return Settings.keepGroupsOpen() || groupOf(selected()) == group;
    }

    /** Whether the row has a world container tab. */
    static boolean hasWorld() {
        for (Tab tab : row()) if (tab.id().equals(TabId.WORLD)) return true;
        return false;
    }

    /** {@code id}'s tab on the row, or null. */
    public static @Nullable Tab tab(TabId id) {
        for (Tab tab : row) if (tab.id().equals(id)) return tab;
        return null;
    }

    /** The tab drawn selected: the one just clicked, else the one the server has in front. */
    public static TabId selected() {
        return clicked != null ? clicked : shown;
    }

    /** The tab the server has in front. */
    public static TabId shown() {
        return shown;
    }

    /** The item tab whose screen is in front, or null when it's the inventory or the world container. */
    public static @Nullable Tab shownItem() {
        return shown.type() == TabId.Type.ITEM ? tab(shown) : null;
    }

    /** Asks for {@code id}'s screen: a tab click, or the wheel. */
    public static void pick(TabId id) {
        if (id.equals(selected()) || !ClientPlayNetworking.canSend(PickC2S.TYPE)) return;
        clicked = id;
        ClientPlayNetworking.send(new PickC2S(id));
    }

    /**
     * Where the item in {@code slot} sits, as the server names it: the inventory, the world
     * container while it's on screen, or inside the item in front. Null for anything else.
     */
    static @Nullable ItemPath pathOf(Slot slot) {
        if (slot.container instanceof Inventory) {
            return slot.getContainerSlot() < 36 ? new ItemPath(ItemRef.Source.INVENTORY, slot.getContainerSlot(), List.of()) : null;
        }
        if (shown.equals(TabId.WORLD)) return new ItemPath(ItemRef.Source.CONTAINER, slot.getContainerSlot(), List.of());
        Tab front = shownItem();
        if (front == null || front.id().path() == null) return null;
        Openable kind = Openable.of(front.item());
        if (kind == Openable.ENDER_CHEST) return new ItemPath(ItemRef.Source.ENDER_CHEST, slot.getContainerSlot(), List.of());
        int index = kind == Openable.BUNDLE ? Groups.bundleIndex(slot) : slot.getContainerSlot();
        if (index < 0) return null;
        ItemPath path = front.id().path();
        List<Integer> inside = new ArrayList<>(path.inside());
        inside.add(index);
        return new ItemPath(path.source(), path.slot(), inside);
    }

    /**
     * Whether the item in {@code slot} shows open: it has a tab, or with every box tabbed,
     * it's the one in front. Only while the row shows.
     */
    static boolean looksOpen(Slot slot) {
        if (!showing()) return false;
        ItemPath path = pathOf(slot);
        if (path == null) return false;
        if (Settings.everyBox()) return path.equals(shown.path());
        for (Tab tab : row) if (path.equals(tab.id().path())) return true;
        return false;
    }

    /** What the client knows of the item in front, for the code both sides run ({@link Shown}). */
    static final Shown.ClientView VIEW = new Shown.ClientView() {
        @Override public ItemStack item() {
            Tab front = shownItem();
            return front == null ? ItemStack.EMPTY : front.item();
        }

        @Override public boolean holds(Container container, int slot) {
            Tab front = shownItem();
            ItemPath path = front == null ? null : front.id().path();
            return path != null && path.source() == ItemRef.Source.INVENTORY && container instanceof Inventory
                    && path.slot() == slot;
        }
    };
}
