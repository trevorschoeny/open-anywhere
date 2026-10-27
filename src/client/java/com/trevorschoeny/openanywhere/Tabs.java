package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemSources;

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
    private static boolean frozen;
    // The tab just clicked, drawn selected until the server's answer comes.
    private static @Nullable TabId clicked;

    /** The server's row. */
    static void receive(TabsS2C payload) {
        row = payload.row();
        shown = payload.shown();
        frozen = payload.frozen();
        clicked = null;
    }

    /** Back in the world: the row is gone with the screens. */
    static void clear() {
        row = List.of();
        shown = TabId.INVENTORY;
        frozen = false;
        clicked = null;
    }

    /** Whether the item in front is off its place (on the cursor): its screen's slots are locked and greyed. */
    public static boolean frozen() {
        return frozen && shownItem() != null;
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

    /** The main tabs, in the row's order, while it shows: MenuKit's group keys. */
    public static List<TabId> mains() {
        List<TabId> ids = new ArrayList<>();
        for (Tab tab : row()) if (!tab.sub()) ids.add(tab.id());
        return ids;
    }

    /**
     * What's in main tab {@code main}, in order: MenuKit's keys for its sub-tabs. An open box
     * in it is a main tab too, so MenuKit stands that box's group here, in its place.
     */
    public static List<TabId> subs(TabId main) {
        List<TabId> ids = new ArrayList<>();
        for (Tab tab : row()) if (main.equals(tab.parent())) ids.add(tab.id());
        return ids;
    }

    /** Whether {@code id} is a main tab on the row: an open container, its lid drawn up. */
    public static boolean open(TabId id) {
        Tab tab = tab(id);
        return tab != null && !tab.sub();
    }

    /** Whether {@code id} is {@code container}, or sits in it, however deep. */
    public static boolean holds(TabId container, TabId id) {
        for (TabId at = id; at != null; ) {
            if (at.equals(container)) return true;
            Tab tab = tab(at);
            at = tab == null ? null : tab.parent();
        }
        return false;
    }

    /**
     * Every tab in where-it-sits order, for the wheel: each container, then each tab inside
     * it, open or not, by place ({@link ServerTabs#ORDER}), each followed by what's inside it.
     */
    static List<TabId> walk() {
        List<TabId> out = new ArrayList<>();
        for (Tab tab : row()) if (tab.parent() == null) walk(tab.id(), out);
        return out;
    }

    private static void walk(TabId id, List<TabId> out) {
        out.add(id);
        List<TabId> inside = new ArrayList<>();
        for (Tab tab : row()) if (id.equals(tab.parent())) inside.add(tab.id());
        inside.sort(java.util.Comparator.comparing(TabId::path, ServerTabs.ORDER));
        for (TabId c : inside) walk(c, out);
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
     * Where the item in {@code slot} sits, as the server names it: the inventory, a carried
     * attachment, the world container while it's on screen, or inside the item in front. Null for anything else.
     */
    static @Nullable ItemPath pathOf(Slot slot) {
        // The player's own slots, named as the server names them: the inventory's main 36 and a
        // carried attachment's. The ender chest's aren't, here: the client's is a stand-in.
        ItemPath own = ItemSources.pathOf(Minecraft.getInstance().player, slot);
        if (own != null) return own;
        if (slot.container instanceof Inventory) return null;
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
        return new ItemPath(path.source(), path.slot(), inside, path.attachment());
    }

    /**
     * Whether the item in {@code slot} shows open: it's a main tab on the row, the same rule
     * as its tab's lid ({@link #open}). Only while the row shows.
     */
    public static boolean looksOpen(Slot slot) {
        if (!showing()) return false;
        ItemPath path = pathOf(slot);
        return path != null && open(TabId.item(path));
    }

    /** What the client knows of the item in front, for the code both sides run ({@link Shown}). */
    static final Shown.ClientView VIEW = new Shown.ClientView() {
        @Override public ItemStack item() {
            Tab front = shownItem();
            return front == null ? ItemStack.EMPTY : front.item();
        }

        @Override public boolean locked(Container container) {
            return frozen() && !(container instanceof Inventory);
        }
    };
}
