package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.item.LiveItem;
import com.trevlar.slotkit.api.menu.MenuSwitch;
import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.storage.StorageContainerAdapter;
import com.trevorschoeny.openanywhere.api.OpenItems;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The tabs, on the server, where every rule is checked.
 *
 * <p>Every tab is a real screen. The row reads: the player's inventory, the container they
 * opened in the world (a chest, a barrel, a placed ender chest), then the items, in a
 * fixed order by where they sit ({@link #ORDER}). Clicking a tab switches to its screen
 * through SlotKit's {@link MenuSwitch}: the world container waits open behind the others,
 * its lid up, and every other screen really closes when the player leaves it.
 *
 * <p>Which items have tabs is the player's setting ({@link PrefsC2S}): the ones they opened
 * by hand (a double-click marks one, again unmarks it), or every box, bundle and ender chest
 * in reach. An item open on screen is a {@link LiveItem}: tied to the item, not its slot, so
 * its screen closes the moment the item is moved by anything else, and every change is
 * written back up to the top-level slot as it's made. Nothing is lost when it closes.
 */
public final class ServerTabs {

    private ServerTabs() {}

    /** An item the player opened by hand: where it sits, and the top-level stack it's in, by identity. */
    private record Mark(ItemPath path, ItemStack top) {}

    /**
     * The item tab whose screen is in front: its menu, its tab, what it is, the live item
     * (null for the ender chest, which is the player's own), and the top-level slot it's in.
     */
    private record Front(AbstractContainerMenu menu, TabId id, Openable kind, @Nullable LiveItem live,
                         Container top, int topSlot) {}

    private static final class State {
        PrefsC2S prefs = PrefsC2S.DEFAULT;
        final List<Mark> marks = new ArrayList<>();
        @Nullable Front front;
        List<Tab> sentRow = List.of();
        TabId sentShown = TabId.INVENTORY;
    }

    // Weak by player: a player who left takes their tabs with them. Synchronized: the slot
    // lock asks it from the client thread too, in singleplayer.
    private static final Map<ServerPlayer, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    // The menus this mod opened: tabs, never the world container.
    private static final Set<AbstractContainerMenu> OURS = Collections.newSetFromMap(new WeakHashMap<>());

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, p -> new State());
    }

    // The fixed order

    /**
     * Where an item sits decides its place on the row: the inventory's own slots first
     * (hotbar first, then slot order), then the world container's, then what's inside
     * those, a level at a time. The ender chest's contents are one level in, since the ender
     * chest is a box itself.
     */
    static final Comparator<ItemPath> ORDER = Comparator
            .<ItemPath>comparingInt(p -> p.depth() + (p.source() == ItemRef.Source.ENDER_CHEST ? 1 : 0))
            .thenComparingInt(p -> switch (p.source()) {
                case INVENTORY -> 0;
                case CONTAINER -> 1;
                case ENDER_CHEST -> 2;
            })
            .thenComparingInt(ItemPath::slot)
            .thenComparing(ItemPath::inside, (a, b) -> {
                for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                    int c = Integer.compare(a.get(i), b.get(i));
                    if (c != 0) return c;
                }
                return Integer.compare(a.size(), b.size());
            });

    // The world container

    /**
     * The container the player opened in the world, waiting behind or in front, or null: a
     * chest's menu (a chest, a large chest, a barrel, a placed ender chest, a minecart) or a
     * placed shulker box's.
     */
    static @Nullable AbstractContainerMenu world(ServerPlayer player) {
        AbstractContainerMenu menu = MenuSwitch.setAside(player).orElse(player.containerMenu);
        boolean container = menu instanceof ChestMenu && !ContainerHost.is(menu) || menu instanceof ShulkerBoxMenu;
        return container && !OURS.contains(menu) ? menu : null;
    }

    /** The world container's own slots: its first slot's container, the chest or box, not the inventory. */
    private static Container containerOf(AbstractContainerMenu world) {
        return world.getSlot(0).container;
    }

    /** A placed ender chest is the world container: the ender chest's own tab would be a second copy of it. */
    private static boolean worldIsEnderChest(ServerPlayer player, @Nullable AbstractContainerMenu world) {
        return world != null && containerOf(world) == player.getEnderChestInventory();
    }

    /** The top-level container a path starts in. */
    private static @Nullable Container topOf(ServerPlayer player, @Nullable AbstractContainerMenu world, ItemPath path) {
        return switch (path.source()) {
            case INVENTORY -> player.getInventory();
            case ENDER_CHEST -> player.getEnderChestInventory();
            case CONTAINER -> world == null ? null : containerOf(world);
        };
    }

    /** The live item at {@code path}, or null when nothing openable is there. */
    private static @Nullable ItemStack at(ServerPlayer player, @Nullable AbstractContainerMenu world, ItemPath path) {
        ItemRef ref = path.source() == ItemRef.Source.CONTAINER
                ? (world == null ? null : ItemSources.at(containerOf(world), path))
                : ItemSources.at(player, path);
        return ref == null || Openable.of(ref.stack()) == null ? null : ref.stack();
    }

    // The row

    /** One item on the row: where it sits, a copy of it, and the name of the item it's in, if any. */
    private record Item(ItemPath path, ItemStack stack, Optional<Component> in) {}

    /** The player's row, in order: the inventory, the world container, then the items. */
    private static List<Tab> row(ServerPlayer player, State st) {
        AbstractContainerMenu world = world(player);
        List<Tab> row = new ArrayList<>();
        row.add(Tab.of(TabId.INVENTORY));
        if (world != null) row.add(Tab.of(TabId.WORLD));
        List<Item> items = st.prefs.everyBox() ? everything(player, world, st.prefs) : marked(player, world, st);
        items.sort(Comparator.comparing(Item::path, ORDER));
        // One ender chest tab, however many ender chests the player carries: the first one's.
        boolean ender = worldIsEnderChest(player, world);
        for (Item item : items) {
            if (Openable.of(item.stack()) == Openable.ENDER_CHEST) {
                if (ender) continue;
                ender = true;
            }
            row.add(new Tab(TabId.item(item.path()), item.stack(), item.in()));
        }
        return row;
    }

    /** The items the player opened by hand, dropping each one no longer where it was. */
    private static List<Item> marked(ServerPlayer player, @Nullable AbstractContainerMenu world, State st) {
        List<Item> items = new ArrayList<>();
        st.marks.removeIf(m -> !stillThere(player, world, m));
        for (Mark m : st.marks) items.add(new Item(m.path(), at(player, world, m.path()).copy(), parentName(player, world, m.path())));
        return items;
    }

    /** A mark holds while its top-level slot holds the same stack and an openable item is still at its place. */
    private static boolean stillThere(ServerPlayer player, @Nullable AbstractContainerMenu world, Mark m) {
        Container top = topOf(player, world, m.path());
        return top != null && top.getItem(m.path().slot()) == m.top() && at(player, world, m.path()) != null;
    }

    /**
     * Every box, bundle and ender chest in reach, of the kinds the player opens: in the
     * inventory, in the world container, inside those, and, when there's an ender chest
     * among them, in the ender chest. A stack of boxes (Carpet) never opens.
     */
    private static List<Item> everything(ServerPlayer player, @Nullable AbstractContainerMenu world, PrefsC2S prefs) {
        Predicate<ItemStack> opens = s -> {
            Openable kind = Openable.of(s);
            return kind != null && prefs.opens(kind) && (kind != Openable.SHULKER || s.getCount() == 1);
        };
        ItemScope scope = ItemScope.inventory().shulkers(true).bundles(true);
        boolean worldEnder = worldIsEnderChest(player, world);
        List<ItemSources.Found> found = new ArrayList<>(ItemSources.find(player, scope, opens));
        if (world != null && !worldEnder) found.addAll(ItemSources.find(containerOf(world), scope, opens));
        // The ender chest's contents are in reach when an ender chest is, or when it's the world container.
        boolean ender = worldEnder || found.stream().anyMatch(f -> Openable.of(f.stack()) == Openable.ENDER_CHEST);
        if (ender) {
            for (ItemSources.Found f : ItemSources.find(player, scope.enderChest(true), opens)) {
                if (f.path().source() == ItemRef.Source.ENDER_CHEST) found.add(f);
            }
        }
        List<Item> items = new ArrayList<>();
        for (ItemSources.Found f : found) items.add(new Item(f.path(), f.stack(), parentName(player, world, f.path())));
        return items;
    }

    /** The name of the item {@code path}'s item is inside, for its tooltip; empty for a top-level item. */
    private static Optional<Component> parentName(ServerPlayer player, @Nullable AbstractContainerMenu world, ItemPath path) {
        if (path.depth() == 0) return Optional.empty();
        ItemStack parent = at(player, world, parentOf(path));
        return parent == null ? Optional.empty() : Optional.of(parent.getHoverName());
    }

    private static ItemPath parentOf(ItemPath path) {
        return new ItemPath(path.source(), path.slot(), path.inside().subList(0, path.depth() - 1));
    }

    private static ItemPath childOf(ItemPath path, int index) {
        List<Integer> inside = new ArrayList<>(path.inside());
        inside.add(index);
        return new ItemPath(path.source(), path.slot(), inside);
    }

    /** Which tab's screen is in front. */
    private static TabId shown(ServerPlayer player, State st) {
        if (st.front != null && st.front.menu() == player.containerMenu) return st.front.id();
        AbstractContainerMenu world = world(player);
        if (world != null && world == player.containerMenu) return TabId.WORLD;
        return TabId.INVENTORY;
    }

    // Set while a tab switch opens its own screen: the switch sends the row once it's done.
    private static boolean switching;

    /**
     * The player opened a screen ({@code ServerPlayer.openMenu}): the row goes with it, so the
     * client draws the screen's first frame with its tab in front. A tab switch sends its own.
     */
    public static void opened(ServerPlayer player) {
        if (!switching) sync(player, state(player));
    }

    /** Sends the row when it or the tab in front changed. */
    private static void sync(ServerPlayer player, State st) {
        if (!ServerPlayNetworking.canSend(player, TabsS2C.TYPE)) return;
        List<Tab> row = row(player, st);
        TabId shown = shown(player, st);
        if (shown.equals(st.sentShown) && sameRow(row, st.sentRow)) return;
        st.sentRow = row;
        st.sentShown = shown;
        ServerPlayNetworking.send(player, new TabsS2C(row, shown));
    }

    private static boolean sameRow(List<Tab> a, List<Tab> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).same(b.get(i))) return false;
        return true;
    }

    /**
     * Every tick: the item in front is forgotten once its screen has gone, and each player
     * hears their row when it changed (an item moved, came or went, a screen switched).
     */
    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State st = state(player);
            if (st.front != null && st.front.menu() != player.containerMenu) st.front = null;
            sync(player, st);
        }
    }

    // What the player does

    /** The player's settings for their row. */
    static void prefs(ServerPlayer player, PrefsC2S prefs) {
        state(player).prefs = prefs;
    }

    /** The player went back to the world: what they opened by hand closes with the screens. */
    static void done(ServerPlayer player) {
        State st = state(player);
        st.marks.clear();
        st.front = null;
    }

    /** A tab click: its screen comes up, if the tab is on the player's row. */
    static void pick(ServerPlayer player, TabId id) {
        State st = state(player);
        if (id.equals(shown(player, st))) return;
        boolean onRow = false;
        for (Tab tab : row(player, st)) onRow |= tab.id().equals(id);
        if (!onRow) return;
        switch (id.type()) {
            case INVENTORY -> MenuSwitch.openInventory(player);
            case WORLD -> MenuSwitch.back(player);
            case ITEM -> openItem(player, st, id.path());
        }
        sync(player, st);
    }

    /**
     * A double-click on the item in {@code menuSlot} of the open menu, or a click with the
     * Open key held. Every box has a tab: it shows that one. Otherwise it opens the item as a
     * tab, or closes it when it's open already. A refused request changes nothing, so a
     * modified client gains nothing by sending one.
     *
     * <p>{@code fromCursor}: the double-click's first click was vanilla's and lifted the item
     * onto the cursor, a brief lift that is accepted. It goes back into its now-empty slot
     * first, through the slot's own rules, then opens as if never lifted.
     */
    static void open(ServerPlayer player, int menuSlot, boolean fromCursor) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return;
        if (fromCursor) {
            ItemStack carried = menu.getCarried();
            Slot back = menu.getSlot(menuSlot);
            if (Openable.of(carried) == null || back.hasItem() || !back.mayPlace(carried)
                    || back.getMaxStackSize(carried) < carried.getCount()) return;
            back.setByPlayer(carried);
            menu.setCarried(ItemStack.EMPTY);
            menu.broadcastChanges();
        }
        if (!menu.getCarried().isEmpty()) return;

        State st = state(player);
        AbstractContainerMenu world = world(player);
        Slot slot = menu.getSlot(menuSlot);
        ItemPath path = pathOf(player, st, world, slot);
        ItemStack stack = slot.getItem();
        Openable kind = Openable.of(stack);
        if (path == null || kind == null) return;
        // A stack of boxes (Carpet) shares one contents list: editing it would copy items
        // into every box (QuickShulker #68).
        if (kind == Openable.SHULKER && stack.getCount() != 1) return;
        // At a placed ender chest the ender chest is already on the row.
        if (kind == Openable.ENDER_CHEST && worldIsEnderChest(player, world)) return;

        if (st.prefs.everyBox()) {
            pick(player, tabFor(player, st, path, kind));
            return;
        }
        Mark mark = markFor(st, player, world, path, kind);
        if (mark != null) {
            st.marks.remove(mark);
            if (st.front != null && st.front.id().equals(TabId.item(mark.path()))) leave(player, world);
            sync(player, st);
            return;
        }
        Container top = topOf(player, world, path);
        if (top == null) return;
        st.marks.add(new Mark(path, top.getItem(path.slot())));
        pick(player, TabId.item(path));
    }

    /** The close button on item tab {@code id}: it closes, and its screen gives way if it's in front. */
    static void close(ServerPlayer player, TabId id) {
        State st = state(player);
        if (id.path() == null) return;
        st.marks.removeIf(m -> m.path().equals(id.path()));
        if (st.front != null && st.front.id().equals(id)) leave(player, world(player));
        sync(player, st);
    }

    /** Off the item in front: back to the world container, or to the inventory without one. */
    private static void leave(ServerPlayer player, @Nullable AbstractContainerMenu world) {
        if (world != null) MenuSwitch.back(player);
        else MenuSwitch.openInventory(player);
    }

    /** The row's tab for the item at {@code path}: the ender chest has one tab, wherever it was double-clicked. */
    private static TabId tabFor(ServerPlayer player, State st, ItemPath path, Openable kind) {
        if (kind != Openable.ENDER_CHEST) return TabId.item(path);
        for (Tab tab : row(player, st)) {
            if (Openable.of(tab.item()) == Openable.ENDER_CHEST) return tab.id();
        }
        return TabId.item(path);
    }

    /** The mark for the item at {@code path}, or for any ender chest when it's one: the ender chest is one. */
    private static @Nullable Mark markFor(State st, ServerPlayer player, @Nullable AbstractContainerMenu world, ItemPath path, Openable kind) {
        for (Mark m : st.marks) {
            if (m.path().equals(path)) return m;
            if (kind == Openable.ENDER_CHEST) {
                ItemStack marked = at(player, world, m.path());
                if (marked != null && Openable.of(marked) == Openable.ENDER_CHEST) return m;
            }
        }
        return null;
    }

    /**
     * Where the item in {@code slot} of the open menu sits: the inventory, the world
     * container, the ender chest on screen, or inside the box or bundle on screen. Null for a
     * slot holding none of these (armor, the crafting grid).
     */
    private static @Nullable ItemPath pathOf(ServerPlayer player, State st, @Nullable AbstractContainerMenu world, Slot slot) {
        if (slot.container == player.getInventory()) {
            return slot.getContainerSlot() < 36 ? new ItemPath(ItemRef.Source.INVENTORY, slot.getContainerSlot(), List.of()) : null;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (world != null && menu == world && slot.container == containerOf(world)) {
            ItemRef.Source source = worldIsEnderChest(player, world) ? ItemRef.Source.ENDER_CHEST : ItemRef.Source.CONTAINER;
            return new ItemPath(source, slot.getContainerSlot(), List.of());
        }
        Front front = st.front;
        if (front == null || front.menu() != menu || front.id().path() == null) return null;
        return switch (front.kind()) {
            case ENDER_CHEST -> slot.container == player.getEnderChestInventory()
                    ? new ItemPath(ItemRef.Source.ENDER_CHEST, slot.getContainerSlot(), List.of()) : null;
            case SHULKER -> slot.container instanceof StorageContainerAdapter ? childOf(front.id().path(), slot.getContainerSlot()) : null;
            case BUNDLE -> {
                int index = Groups.bundleIndex(slot);
                yield index < 0 ? null : childOf(front.id().path(), index);
            }
        };
    }

    // Opening an item's screen

    /**
     * Opens the item at {@code path} on its own screen: a shulker box on vanilla's shulker box
     * screen over the item, the ender chest on vanilla's ender chest screen, a bundle on the host.
     * One player per item: two players can't have the same item open.
     */
    private static void openItem(ServerPlayer player, State st, @Nullable ItemPath path) {
        if (path == null) return;
        AbstractContainerMenu world = world(player);
        ItemStack item = at(player, world, path);
        Container top = topOf(player, world, path);
        if (item == null || top == null) return;
        Openable kind = Openable.of(item);
        if (kind == Openable.ENDER_CHEST) {
            // The player's own ender chest, read and written in place: every placed ender chest
            // is these same 27 slots.
            MenuProvider ender = new SimpleMenuProvider((id, inventory, p) -> ours(ChestMenu.threeRows(id, inventory, player.getEnderChestInventory())),
                    Component.translatable("container.enderchest"));
            show(player, st, ender, path, kind, null, top);
            return;
        }
        if (openByAnother(player, top, path)) return;
        LiveItem live = path.source() == ItemRef.Source.CONTAINER ? ItemSources.live(top, path) : ItemSources.live(player, path);
        if (live == null) return;
        Component title = live.stack().getHoverName();
        if (kind == Openable.SHULKER) {
            Storage storage = live.storage(new ShulkerStorage(live.stack(), OpenItems.boxSizeOf(live.stack())));
            Container box = new BoxContainer(storage, live);
            show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(new ShulkerBoxMenu(id, inventory, box)), title),
                    path, kind, live, top);
            return;
        }
        BundleStorage contents = new BundleStorage(live.stack());
        Storage storage = live.storage(contents);
        Container validity = new SimpleContainer(0) {
            @Override public boolean stillValid(Player p) { return !live.isStale(); }
        };
        if (show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(ContainerHost.create(id, inventory, validity)), title),
                path, kind, live, top)) {
            Groups.addBundle(player, storage, contents);
            player.containerMenu.broadcastChanges();
        }
    }

    /** Switches to {@code provider}'s screen as item tab {@code path}. Whether it opened. */
    private static boolean show(ServerPlayer player, State st, MenuProvider provider, ItemPath path, Openable kind,
                                @Nullable LiveItem live, Container top) {
        OptionalInt opened;
        switching = true;
        try {
            opened = MenuSwitch.open(player, provider);
        } finally {
            switching = false;
        }
        if (opened.isEmpty()) return false;
        st.front = new Front(player.containerMenu, TabId.item(path), kind, live, top, path.slot());
        return true;
    }

    /**
     * An opened box's container: its storage, with vanilla's validity check tied to the live
     * item, so once the box has moved vanilla refuses clicks into it and closes it, and the
     * box item for other mods ({@link OpenItems.Box}).
     */
    private static final class BoxContainer extends StorageContainerAdapter implements OpenItems.Box {
        private final LiveItem live;

        BoxContainer(Storage storage, LiveItem live) {
            super(storage);
            this.live = live;
        }

        @Override public boolean stillValid(Player player) { return !live.isStale(); }

        @Override public ItemStack stack() { return live.stack(); }
    }

    private static <M extends AbstractContainerMenu> M ours(M menu) {
        OURS.add(menu);
        return menu;
    }

    /** Whether another player has the item at {@code path} in {@code top} on screen. */
    private static boolean openByAnother(ServerPlayer player, Container top, ItemPath path) {
        synchronized (STATES) {
            for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
                Front f = e.getValue().front;
                if (e.getKey() != player && f != null && f.menu() == e.getKey().containerMenu && f.top() == top
                        && path.equals(f.id().path())) return true;
            }
        }
        return false;
    }

    // What the other classes ask

    /** The item {@code player} has in front, the live stack itself; empty for none. */
    static ItemStack frontItem(ServerPlayer player) {
        State st = STATES.get(player);
        Front f = st == null ? null : st.front;
        if (f == null || f.menu() != player.containerMenu) return ItemStack.EMPTY;
        return f.live() != null ? f.live().stack() : f.top().getItem(f.topSlot());
    }

    /** Which kind {@code player} has in front, or null. */
    static @Nullable Openable frontKind(ServerPlayer player) {
        State st = STATES.get(player);
        Front f = st == null ? null : st.front;
        return f == null || f.menu() != player.containerMenu ? null : f.kind();
    }

    /** Whether some player has, in front, the item {@code container}'s slot {@code slot} holds (or holds it inside). */
    static boolean holds(Container container, int slot) {
        synchronized (STATES) {
            for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
                Front f = e.getValue().front;
                if (f != null && f.menu() == e.getKey().containerMenu && f.top() == container && f.topSlot() == slot) return true;
            }
        }
        return false;
    }
}
