package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.trevlar.slotkit.api.slot.OnDemandGroup;
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
 * <p>Every tab is a real screen. Clicking one switches to its screen through SlotKit's
 * {@link MenuSwitch}: the world container waits open behind the others, its lid up, and
 * every other screen really closes when the player leaves it.
 *
 * <p>The row is a tree ({@link #row}). "Open" means a tab is on the row; "selected" means
 * it's the one in front. The inventory and the placed container are always open. A box is
 * open while it's selected or something inside it is (the line up from the selected tab),
 * and an open box is a main tab, its boxes its sub-tabs. Which other boxes have tabs is the
 * player's setting ({@link PrefsC2S}): opened by hand (the default), only that open line;
 * every box, also each box in an open container, as a sub-tab under it, and Pocket Ender
 * Chest's ender chest stays open once it has been opened.
 *
 * <p>An item open on screen is a {@link LiveItem}: tied to the item, not its slot, so its
 * screen closes the moment the item is moved by anything else, and every change is written
 * back up to the top-level slot as it's made. Nothing is lost when it closes.
 */
public final class ServerTabs {

    private ServerTabs() {}

    /**
     * The item in front, opened by hand, now on the cursor, its tab closed: it opens again,
     * in front, where it's put down. Gone when it leaves the cursor any other way, or when the
     * screens close.
     */
    private record Lifted(ItemPath path, ItemStack item) {}

    /**
     * The item tab whose screen is in front. It follows its item: moved, the screen stays
     * and its slots now write to the item's new place; on the cursor with every box tabbed,
     * the screen stays too, its slots locked and showing the item as it was ({@code frozen}).
     */
    private static final class Front {
        final AbstractContainerMenu menu;
        final Openable kind;
        TabId id;
        // The storage behind its slots, following the item; null for the ender chest, the player's own.
        final @Nullable Anchor anchor;
        // The top-level slot it's in, and the stack there (by identity).
        Container top;
        int topSlot;
        ItemStack topStack;
        // The item as last seen in place, to know it again on the cursor or elsewhere.
        ItemStack seen;
        // The container its own slots are in, which the lock closes while it's frozen.
        @Nullable Container slots;
        // The open bundle's slots, rebound when it moves.
        OnDemandGroup.@Nullable Instance bundle;
        boolean frozen;

        Front(AbstractContainerMenu menu, Openable kind, ItemPath path, @Nullable Anchor anchor, Container top, ItemStack seen) {
            this.menu = menu;
            this.kind = kind;
            this.id = TabId.item(path);
            this.anchor = anchor;
            this.top = top;
            this.topSlot = path.slot();
            this.topStack = top.getItem(path.slot());
            this.seen = seen;
        }
    }

    private static final class State {
        PrefsC2S prefs = PrefsC2S.DEFAULT;
        // Pocket Ender Chest's ender chest was opened with every box tabbed: it stays open until the screens close.
        boolean pocketOpened;
        final List<Lifted> lifted = new ArrayList<>();
        @Nullable Front front;
        List<Tab> sentRow = List.of();
        @Nullable TabId sentShown = TabId.INVENTORY;
        boolean sentFrozen;
        // Each top-level container's slots as last tick ended, by identity ({@link #find}),
        // keyed by its slot 0's path: a carried attachment's container is new each time.
        final Map<ItemPath, ItemStack[]> before = new java.util.HashMap<>();
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
     * (hotbar first, then slot order), then carried attachments' (another mod's pocket or
     * ender chest slot), then the world container's, then what's inside those, a level at a
     * time. The ender chest's contents are one level in, since the ender
     * chest is a box itself.
     */
    static final Comparator<ItemPath> ORDER = Comparator
            .<ItemPath>comparingInt(p -> p.depth() + (p.source() == ItemRef.Source.ENDER_CHEST ? 1 : 0))
            .thenComparingInt(p -> switch (p.source()) {
                case INVENTORY -> 0;
                case CARRIED -> 1;
                case CONTAINER -> 2;
                case ENDER_CHEST -> 3;
            })
            .thenComparing(p -> String.valueOf(p.attachment()))
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
            // A new container each call, over the same stacks: compare its stacks, never it.
            case CARRIED -> ItemSources.containerOf(player, path);
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

    /** One item on the row: where it sits, and a copy of it. */
    private record Item(ItemPath path, ItemStack stack) {}

    /**
     * What the row is built from this tick: the world container (or null), whether it's the
     * player's own ender chest, placed, and the one ender chest item that stands for the ender
     * chest ({@link #enderItem}; null when there's none, or when the world container is it).
     */
    private record Reach(@Nullable AbstractContainerMenu world, boolean worldEnder, @Nullable ItemPath ender) {
        /** Whether the ender chest's item is Pocket Ender Chest's (a carried slot's): it stands after the world container. */
        boolean pocket() {
            return ender != null && ender.source() == ItemRef.Source.CARRIED;
        }
    }

    private static Reach reach(ServerPlayer player) {
        AbstractContainerMenu world = world(player);
        boolean worldEnder = worldIsEnderChest(player, world);
        return new Reach(world, worldEnder, worldEnder ? null : enderItem(player, world));
    }

    /**
     * The ender chest item that is the ender chest's one tab, however many the player carries:
     * the one in a carried slot (Pocket Ender Chest's) first, as it's always in reach, else the
     * first in the row's order.
     */
    private static @Nullable ItemPath enderItem(ServerPlayer player, @Nullable AbstractContainerMenu world) {
        Predicate<ItemStack> ender = s -> Openable.of(s) == Openable.ENDER_CHEST;
        ItemScope scope = ItemScope.inventory().shulkers(true).bundles(true).carried(true);
        List<ItemPath> found = new ArrayList<>();
        for (ItemSources.Found f : ItemSources.find(player, scope, ender)) found.add(f.path());
        if (world != null) for (ItemSources.Found f : ItemSources.find(containerOf(world), scope, ender)) found.add(f.path());
        return found.stream()
                .min(Comparator.<ItemPath>comparingInt(p -> p.source() == ItemRef.Source.CARRIED ? 0 : 1).thenComparing(ORDER))
                .orElse(null);
    }

    /**
     * The tab of the container the item at {@code path} sits in: the box or bundle around it,
     * the ender chest (its item's tab, or the world tab at a placed ender chest), the world
     * container, or the inventory (a carried slot is the inventory's too).
     */
    private static TabId parentTab(Reach r, ItemPath path) {
        if (path.depth() > 0) return TabId.item(parentOf(path));
        return switch (path.source()) {
            case INVENTORY, CARRIED -> TabId.INVENTORY;
            case CONTAINER -> TabId.WORLD;
            case ENDER_CHEST -> r.worldEnder() ? TabId.WORLD : r.ender() != null ? TabId.item(r.ender()) : TabId.INVENTORY;
        };
    }

    /**
     * The open line: item tab {@code id} and every box it sits in, up to the inventory or world
     * tab (not included). Empty for none.
     */
    private static List<TabId> line(Reach r, @Nullable TabId id) {
        List<TabId> line = new ArrayList<>();
        for (TabId at = id; at != null && at.type() == TabId.Type.ITEM; at = parentTab(r, at.path())) line.add(at);
        return line;
    }

    /**
     * The player's row as a tree, laid out in the order it's drawn:
     * <ul>
     *   <li><b>Open:</b> the inventory and the world container, always; the line up from the
     *       tab in front ({@link #line}); and, with every box tabbed, Pocket Ender Chest's
     *       ender chest once it has been opened. Each open tab is a main tab.</li>
     *   <li><b>Sub-tabs</b>, with every box tabbed: each box in an open container that isn't
     *       open itself, under that container's tab. Opened by hand, there are none.</li>
     *   <li><b>Order:</b> the inventory, the world container, then Pocket Ender Chest's ender
     *       chest when it's open. Each main tab is followed by its sub-tabs, then by the open
     *       boxes inside it, each in turn, so an opened box comes right after the group it
     *       sits in. Siblings go by where they sit ({@link #ORDER}).</li>
     *   <li><b>One ender chest tab</b>, however many ender chests the player carries
     *       ({@link #enderItem}); none at a placed ender chest, which is the world tab.</li>
     * </ul>
     */
    private static List<Tab> row(ServerPlayer player, State st) {
        Reach r = reach(player);
        Front f = st.front;
        List<TabId> line = line(r, f == null ? null : f.id);
        boolean every = st.prefs.everyBox();
        TabId ender = r.ender() == null ? null : TabId.item(r.ender());
        if (every && r.pocket() && line.contains(ender)) st.pocketOpened = true;

        Set<TabId> open = new HashSet<>(line);
        open.add(TabId.INVENTORY);
        if (r.world() != null) open.add(TabId.WORLD);
        if (every && r.pocket() && st.pocketOpened) open.add(ender);

        // The items with tabs: every box in an open container, with every box tabbed; then
        // the open ones themselves (the item in front on the cursor as last seen).
        Map<TabId, ItemStack> items = new LinkedHashMap<>();
        if (every) {
            for (Item i : everything(player, r, st.prefs)) {
                if (open.contains(parentTab(r, i.path()))) items.putIfAbsent(TabId.item(i.path()), i.stack());
            }
        }
        for (TabId id : open) {
            if (id.type() != TabId.Type.ITEM || items.containsKey(id)) continue;
            ItemStack stack = at(player, r.world(), id.path());
            if (stack == null && f != null && id.equals(f.id)) stack = f.seen;
            if (stack != null) items.put(id, stack.copy());
        }
        // One ender chest tab: every other ender chest item goes.
        items.keySet().removeIf(id -> Openable.of(items.get(id)) == Openable.ENDER_CHEST && !id.equals(ender));

        Map<TabId, List<TabId>> children = new HashMap<>();
        for (TabId id : items.keySet()) children.computeIfAbsent(parentTab(r, id.path()), k -> new ArrayList<>()).add(id);
        for (List<TabId> c : children.values()) c.sort(Comparator.comparing(TabId::path, ORDER));

        List<Tab> row = new ArrayList<>();
        // Pocket Ender Chest's ender chest, open, stands after the world container as a top-level
        // tab of its own, with no parent, rather than in the inventory's group.
        TabId pocket = r.pocket() && open.contains(ender) && items.containsKey(ender) ? ender : null;
        place(row, TabId.INVENTORY, null, items, children, open, pocket);
        if (r.world() != null) place(row, TabId.WORLD, null, items, children, open, pocket);
        if (pocket != null) place(row, pocket, null, items, children, open, pocket);
        return row;
    }

    /** Main tab {@code id}, its sub-tabs (the closed boxes in it), then each open box in it, in turn. */
    private static void place(List<Tab> row, TabId id, @Nullable TabId parent, Map<TabId, ItemStack> items,
                              Map<TabId, List<TabId>> children, Set<TabId> open, @Nullable TabId pocket) {
        row.add(new Tab(id, items.getOrDefault(id, ItemStack.EMPTY), parent, false));
        List<TabId> inside = children.getOrDefault(id, List.of());
        for (TabId c : inside) if (!open.contains(c)) row.add(new Tab(c, items.get(c), id, true));
        for (TabId c : inside) if (open.contains(c) && !c.equals(pocket)) place(row, c, id, items, children, open, pocket);
    }

    /**
     * Where {@code stack} is now, at the top level: {@code prefer} if it's there, else a slot
     * holding the same item in the inventory, the world container or the ender chest. Null
     * when it's nowhere in reach. Of two identical items (two empty red boxes), the one that
     * just arrived is it: its slot changed since last tick ({@link State#before}), the other's
     * didn't.
     */
    private static @Nullable ItemPath find(ServerPlayer player, @Nullable AbstractContainerMenu world, ItemStack stack, @Nullable ItemPath prefer) {
        if (stack.isEmpty()) return null;
        if (prefer != null) {
            ItemStack there = at(player, world, prefer);
            if (there != null && ItemStack.matches(there, stack)) return prefer;
        }
        State st = state(player);
        ItemPath first = null;
        for (Top top : tops(player, world)) {
            Container c = top.container();
            ItemStack[] before = st.before.get(top.at(0));
            for (int i = 0; i < topSize(c); i++) {
                ItemStack here = c.getItem(i);
                if (!ItemStack.matches(here, stack)) continue;
                ItemPath path = top.at(i);
                if (before == null || i >= before.length || before[i] != here) return path;
                if (first == null) first = path;
            }
        }
        // An identical item that didn't move isn't the one that did, but any ender chest is the ender chest.
        return Openable.of(stack) == Openable.ENDER_CHEST ? first : null;
    }

    /** A top-level container in reach, and where its slots are: {@code at(i)} is its slot {@code i}'s path. */
    private record Top(ItemRef.Source source, net.minecraft.resources.@Nullable Identifier attachment, Container container) {
        ItemPath at(int slot) { return new ItemPath(source, slot, List.of(), attachment); }
    }

    /**
     * The top-level containers in reach, in the row's order: the inventory, the carried
     * attachments, the world container, the ender chest.
     */
    private static List<Top> tops(ServerPlayer player, @Nullable AbstractContainerMenu world) {
        List<Top> tops = new ArrayList<>();
        tops.add(new Top(ItemRef.Source.INVENTORY, null, player.getInventory()));
        // Only the carried attachments holding something, found by a walk: an empty one has
        // nothing to find or remember. If this walk shows up in profiling, ask SlotKit for a
        // list of carried ids instead.
        Set<net.minecraft.resources.Identifier> carried = new java.util.LinkedHashSet<>();
        for (ItemSources.Found f : ItemSources.find(player, ItemScope.inventory().carried(true), s -> true))
            if (f.path().source() == ItemRef.Source.CARRIED) carried.add(f.path().attachment());
        for (var id : carried) {
            ItemPath path = new ItemPath(ItemRef.Source.CARRIED, 0, List.of(), id);
            tops.add(new Top(ItemRef.Source.CARRIED, id, ItemSources.containerOf(player, path)));
        }
        if (world != null && !worldIsEnderChest(player, world)) tops.add(new Top(ItemRef.Source.CONTAINER, null, containerOf(world)));
        tops.add(new Top(ItemRef.Source.ENDER_CHEST, null, player.getEnderChestInventory()));
        return tops;
    }

    /** How many of {@code c}'s slots hold items: the inventory's 36, not its armor and offhand. */
    private static int topSize(Container c) {
        return c instanceof net.minecraft.world.entity.player.Inventory ? 36 : c.getContainerSize();
    }

    /** Notes what's in every top-level slot in reach, by identity, for telling a moved item from its twin next tick. */
    private static void remember(ServerPlayer player, State st) {
        st.before.clear();
        for (Top top : tops(player, world(player))) {
            Container c = top.container();
            ItemStack[] now = new ItemStack[topSize(c)];
            for (int i = 0; i < now.length; i++) now[i] = c.getItem(i);
            st.before.put(top.at(0), now);
        }
    }

    /**
     * Every box, bundle and ender chest in reach, of the kinds the player opens: in the
     * inventory, in the world container, inside those, and, when there's an ender chest
     * among them, in the ender chest. A stack of boxes (Carpet) never opens.
     */
    private static List<Item> everything(ServerPlayer player, Reach r, PrefsC2S prefs) {
        AbstractContainerMenu world = r.world();
        Predicate<ItemStack> opens = s -> {
            Openable kind = Openable.of(s);
            return kind != null && prefs.opens(kind) && (kind != Openable.SHULKER || s.getCount() == 1);
        };
        ItemScope scope = ItemScope.inventory().shulkers(true).bundles(true).carried(true);
        boolean worldEnder = r.worldEnder();
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
        for (ItemSources.Found f : found) items.add(new Item(f.path(), f.stack()));
        return items;
    }

    private static ItemPath parentOf(ItemPath path) {
        return new ItemPath(path.source(), path.slot(), path.inside().subList(0, path.depth() - 1), path.attachment());
    }

    private static ItemPath childOf(ItemPath path, int index) {
        List<Integer> inside = new ArrayList<>(path.inside());
        inside.add(index);
        return new ItemPath(path.source(), path.slot(), inside, path.attachment());
    }

    /** Which tab's screen is in front. */
    private static TabId shown(ServerPlayer player, State st) {
        if (st.front != null && st.front.menu == player.containerMenu) return st.front.id;
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
        boolean frozen = st.front != null && st.front.frozen;
        if (shown.equals(st.sentShown) && frozen == st.sentFrozen && sameRow(row, st.sentRow)) return;  // sentShown null: never sent
        st.sentRow = row;
        st.sentShown = shown;
        st.sentFrozen = frozen;
        ServerPlayNetworking.send(player, new TabsS2C(row, shown, frozen));
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
            if (st.front != null && st.front.menu != player.containerMenu) st.front = null;
            if (!st.prefs.everyBox()) settle(player, st);
            if (st.front != null) follow(player, st);
            sync(player, st);
            remember(player, st);
        }
    }

    /**
     * The item in front, kept up with. In place: its look is noted. On the cursor: with every
     * box tabbed its screen stays, locked and showing it as it was; opened by hand, its tab
     * closes and the screen goes back to its group's, to open again where it's put down
     * ({@link #settle}). Moved: the screen follows it there. Gone: back to its group's screen.
     */
    private static void follow(ServerPlayer player, State st) {
        Front f = st.front;
        AbstractContainerMenu world = world(player);
        ItemPath path = f.id.path();
        // In a world container that's gone (walked away from, through a portal): its screen
        // goes, though the chest's slot still holds the item. Its writes would reach a chest
        // the player is no longer at, one that may already be saved and unloaded.
        if (!reachable(player, f)) {
            goHome(player, st, path);
            return;
        }
        boolean inPlace = !offPlace(f) && (f.anchor != null || at(player, world, path) != null);
        if (inPlace) {
            f.frozen = false;
            f.seen = (f.anchor != null ? f.anchor.live.stack() : at(player, world, path)).copy();
            if (f.anchor != null) f.anchor.seen(f.seen);
            return;
        }
        if (ItemStack.matches(player.containerMenu.getCarried(), f.seen)) {
            if (st.prefs.everyBox()) {
                f.frozen = true;
                return;
            }
            st.lifted.add(new Lifted(path, f.seen.copy()));
            goHome(player, st, path);
            return;
        }
        ItemPath to = find(player, world, f.seen, null);
        if (to == null || !reanchor(player, st, f, to)) goHome(player, st, path);
    }

    /** The item in front moved to {@code to}: its screen's slots now write there. False when it can't be followed. */
    private static boolean reanchor(ServerPlayer player, State st, Front f, ItemPath to) {
        AbstractContainerMenu world = world(player);
        Container top = topOf(player, world, to);
        if (top == null || openByAnother(player, top, to)) return false;
        if (f.anchor != null) {
            LiveItem live = to.source() == ItemRef.Source.CONTAINER ? ItemSources.live(top, to) : ItemSources.live(player, to);
            if (live == null) return false;
            if (f.kind == Openable.SHULKER) {
                f.anchor.rebind(live, live.storage(new ShulkerStorage(live.stack(), f.anchor.size())));
            } else {
                BundleStorage contents = new BundleStorage(live.stack());
                f.anchor.rebind(live, live.storage(contents));
                if (f.bundle != null) contents.shownIn(f.bundle);
            }
        }
        ItemStack topStack = top.getItem(to.slot());
        f.id = TabId.item(to);
        f.top = top;
        f.topSlot = to.slot();
        f.topStack = topStack;
        f.frozen = false;
        player.containerMenu.broadcastChanges();
        return true;
    }

    /**
     * Opened by hand, the item in front, then lifted: put down anywhere in reach, it opens
     * again there, in front. Leaving the cursor any other way, it stays closed.
     */
    private static void settle(ServerPlayer player, State st) {
        if (st.lifted.isEmpty()) return;
        ItemStack carried = player.containerMenu.getCarried();
        AbstractContainerMenu world = world(player);
        for (Lifted l : List.copyOf(st.lifted)) {
            if (ItemStack.matches(carried, l.item())) continue;
            st.lifted.remove(l);
            ItemPath to = find(player, world, l.item(), l.path());
            if (to != null && openItem(player, st, to)) sync(player, st);
        }
    }

    /** Whether the top-level container {@code f}'s item is in is still in reach: the world container, while it's still open. */
    private static boolean reachable(ServerPlayer player, Front f) {
        if (f.id.path() == null || f.id.path().source() != ItemRef.Source.CONTAINER) return true;
        AbstractContainerMenu world = world(player);
        return world != null && containerOf(world) == f.top;
    }

    /** Whether {@code f}'s item has left its place: moved, taken, or on the cursor. */
    private static boolean offPlace(Front f) {
        return f.anchor != null ? f.anchor.live.isStale() : f.top.getItem(f.topSlot) != f.topStack;
    }

    /** The player left: their state goes with them (the map's values reach the player, so it never forgets them on its own). */
    static void left(ServerPlayer player) {
        STATES.remove(player);
    }

    /** A respawned player is a new one: their settings carry over, nothing else. */
    static void respawned(ServerPlayer from, ServerPlayer to) {
        State old = STATES.remove(from);
        if (old != null) state(to).prefs = old.prefs;
    }

    /** The server stopped: nothing of it is kept, in singleplayer least of all. */
    static void stopped() {
        STATES.clear();
        OURS.clear();
    }

    /** The player closed the screens: whatever they carried went back or dropped, and nothing waits to reopen. */
    public static void closed(ServerPlayer player) {
        State st = STATES.get(player);
        if (st != null) st.lifted.clear();
    }

    // What the player does

    /** The player's settings for their row. */
    static void prefs(ServerPlayer player, PrefsC2S prefs) {
        state(player).prefs = prefs;
    }

    /** The player went back to the world: everything they opened closes with the screens. */
    static void done(ServerPlayer player) {
        State st = state(player);
        st.front = null;
        st.pocketOpened = false;
        // The client dropped its row with the screens: the next tick sends it whole again,
        // so the next screen has its tabs from its first frame, changed or not.
        st.sentRow = List.of();
        st.sentShown = null;
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
     * Open key held: the item's screen comes up, its tab selected; on the item in front, it
     * closes, back to the container it sits in. Both modes alike. A refused request changes
     * nothing, so a modified client gains nothing by sending one.
     *
     * <p>{@code fromCursor}: the double-click's first click was vanilla's and lifted the item
     * onto the cursor, a brief lift that is accepted. It goes back into its now-empty slot
     * first, through the slot's own rules, then opens as if never lifted.
     */
    static void open(ServerPlayer player, int menuSlot, boolean fromCursor) {
        AbstractContainerMenu menu = player.containerMenu;
        State st = state(player);
        // The second click of a double-click on an item opened by hand: its first click lifted
        // it, which closed its tab (and left its screen, if it was in front, so the slot
        // number may be another screen's). It goes back where it was and stays closed.
        if (fromCursor && !st.prefs.everyBox()) {
            ItemStack carried = menu.getCarried();
            for (Lifted l : List.copyOf(st.lifted)) {
                Container top = topOf(player, world(player), l.path());
                if (top == null || !ItemStack.matches(carried, l.item()) || !top.getItem(l.path().slot()).isEmpty()) continue;
                top.setItem(l.path().slot(), carried);
                top.setChanged();
                menu.setCarried(ItemStack.EMPTY);
                st.lifted.remove(l);
                menu.broadcastChanges();
                sync(player, st);
                return;
            }
        }
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return;
        // The front's slots while it's off its place: their paths are stale until the tick follows it.
        if (locked(menu.getSlot(menuSlot).container)) return;
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

        TabId tab = tabFor(reach(player), path, kind);
        if (tab.equals(shown(player, st))) goHome(player, st, tab.path());
        else openItem(player, st, tab.path());
        sync(player, st);
    }

    /**
     * Off the item in front, to the container it sits in: the box around it, the ender chest,
     * the world container or the inventory, the nearest of those still in reach. The inventory
     * when none is.
     */
    private static void goHome(ServerPlayer player, State st, @Nullable ItemPath path) {
        st.front = null;
        Reach r = reach(player);
        TabId home = path == null ? TabId.INVENTORY : parentTab(r, path);
        while (home.type() == TabId.Type.ITEM && at(player, r.world(), home.path()) == null) home = parentTab(r, home.path());
        if (home.type() == TabId.Type.ITEM && openItem(player, st, home.path())) return;
        if (home.equals(TabId.WORLD) && r.world() != null) MenuSwitch.back(player);
        else MenuSwitch.openInventory(player);
    }

    /** The row's tab for the item at {@code path}: the ender chest has one tab, wherever it was double-clicked. */
    private static TabId tabFor(Reach r, ItemPath path, Openable kind) {
        return kind == Openable.ENDER_CHEST && r.ender() != null ? TabId.item(r.ender()) : TabId.item(path);
    }

    /**
     * Where the item in {@code slot} of the open menu sits: the inventory, a carried
     * attachment, the ender chest, the world container, or inside the box or bundle on
     * screen. Null for a slot holding none of these (armor, the crafting grid).
     */
    private static @Nullable ItemPath pathOf(ServerPlayer player, State st, @Nullable AbstractContainerMenu world, Slot slot) {
        // The player's own slots: the inventory's main 36, a carried attachment's, the ender chest's.
        ItemPath own = ItemSources.pathOf(player, slot);
        if (own != null) return own;
        if (slot.container == player.getInventory()) return null;
        AbstractContainerMenu menu = player.containerMenu;
        if (world != null && menu == world && slot.container == containerOf(world)) {
            ItemRef.Source source = worldIsEnderChest(player, world) ? ItemRef.Source.ENDER_CHEST : ItemRef.Source.CONTAINER;
            return new ItemPath(source, slot.getContainerSlot(), List.of());
        }
        Front front = st.front;
        if (front == null || front.menu != menu || front.id.path() == null) return null;
        return switch (front.kind) {
            case ENDER_CHEST -> slot.container == player.getEnderChestInventory()
                    ? new ItemPath(ItemRef.Source.ENDER_CHEST, slot.getContainerSlot(), List.of()) : null;
            case SHULKER -> slot.container instanceof StorageContainerAdapter ? childOf(front.id.path(), slot.getContainerSlot()) : null;
            case BUNDLE -> {
                int index = Groups.bundleIndex(slot);
                yield index < 0 ? null : childOf(front.id.path(), index);
            }
        };
    }

    // Opening an item's screen

    /**
     * Opens the item at {@code path} on its own screen: a shulker box on vanilla's shulker box
     * screen over the item, the ender chest on vanilla's ender chest screen, a bundle on the host.
     * One player per item: two players can't have the same item open. Whether it opened.
     */
    private static boolean openItem(ServerPlayer player, State st, @Nullable ItemPath path) {
        if (path == null) return false;
        AbstractContainerMenu world = world(player);
        ItemStack item = at(player, world, path);
        Container top = topOf(player, world, path);
        if (item == null || top == null) return false;
        Openable kind = Openable.of(item);
        if (kind == Openable.ENDER_CHEST) {
            // The player's own ender chest, read and written in place: every placed ender chest
            // is these same 27 slots.
            MenuProvider ender = new SimpleMenuProvider((id, inventory, p) -> ours(ChestMenu.threeRows(id, inventory, player.getEnderChestInventory())),
                    Component.translatable("container.enderchest"));
            Front f = show(player, st, ender, path, kind, null, top, item.copy());
            if (f != null) f.slots = player.getEnderChestInventory();
            return f != null;
        }
        if (openByAnother(player, top, path)) return false;
        LiveItem live = path.source() == ItemRef.Source.CONTAINER ? ItemSources.live(top, path) : ItemSources.live(player, path);
        if (live == null) return false;
        Component title = live.stack().getHoverName();
        if (kind == Openable.SHULKER) {
            int size = OpenItems.boxSizeOf(live.stack());
            Anchor anchor = new Anchor(live, live.storage(new ShulkerStorage(live.stack(), size)), size,
                    seen -> new ShulkerStorage(seen, size));
            BoxContainer box = new BoxContainer(player, anchor);
            Front f = show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(new ShulkerBoxMenu(id, inventory, box)), title),
                    path, kind, anchor, top, live.stack().copy());
            if (f != null) f.slots = box;
            return f != null;
        }
        BundleStorage contents = new BundleStorage(live.stack());
        Anchor anchor = new Anchor(live, live.storage(contents), Openable.BUNDLE.slots, BundleStorage::new);
        Container validity = new SimpleContainer(0) {
            @Override public boolean stillValid(Player p) { return stillOpen(player, anchor); }
        };
        Front f = show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(ContainerHost.create(id, inventory, validity)), title),
                path, kind, anchor, top, live.stack().copy());
        if (f == null) return false;
        f.bundle = Groups.addBundle(player, anchor, contents);
        if (f.bundle != null && !f.bundle.slots().isEmpty()) f.slots = f.bundle.slots().getFirst().container;
        player.containerMenu.broadcastChanges();
        return true;
    }

    /** Switches to {@code provider}'s screen as item tab {@code path}: the front it now is, or null when it didn't open. */
    private static @Nullable Front show(ServerPlayer player, State st, MenuProvider provider, ItemPath path, Openable kind,
                                        @Nullable Anchor anchor, Container top, ItemStack seen) {
        OptionalInt opened;
        switching = true;
        try {
            opened = MenuSwitch.open(player, provider);
        } finally {
            switching = false;
        }
        if (opened.isEmpty()) return null;
        st.front = new Front(player.containerMenu, kind, path, anchor, top, seen);
        return st.front;
    }

    /** Whether the item behind {@code anchor} still has its screen. Vanilla closes the screen when not. */
    private static boolean stillOpen(ServerPlayer player, Anchor anchor) {
        if (!anchor.live.isStale()) return true;
        // Off its place, its screen stays while it's the front: the tick follows the item or
        // goes back to its group's screen ({@link #follow}), rather than vanilla closing every
        // screen. Its slots are locked meanwhile ({@link #locked}).
        State st = STATES.get(player);
        return st != null && st.front != null && st.front.anchor == anchor;
    }

    /**
     * The storage behind an opened item's slots, following the item: its live storage while
     * it's in place, rebound when it moves ({@link #reanchor}); between, and while it's on
     * the cursor, the item as last seen, for showing only (its slots are locked then).
     */
    private static final class Anchor implements Storage {
        LiveItem live;
        private Storage over;
        private final int size;
        private final java.util.function.Function<ItemStack, Storage> look;
        ItemStack seen;
        private @Nullable Storage asSeen;

        Anchor(LiveItem live, Storage over, int size, java.util.function.Function<ItemStack, Storage> look) {
            this.live = live;
            this.over = over;
            this.size = size;
            this.look = look;
            this.seen = live.stack().copy();
        }

        void rebind(LiveItem live, Storage over) {
            this.live = live;
            this.over = over;
            seen(live.stack().copy());
        }

        void seen(ItemStack seen) {
            this.seen = seen;
            asSeen = null;
        }

        private Storage now() {
            if (!live.isStale()) return over;
            if (asSeen == null) asSeen = look.apply(seen.copy());
            return asSeen;
        }

        @Override public ItemStack getStack(int i) { return now().getStack(i); }
        @Override public void setStack(int i, ItemStack stack) { now().setStack(i, stack); }
        @Override public int size() { return size; }
        @Override public void markDirty() { now().markDirty(); }
    }

    /**
     * An opened box's container: its storage, with vanilla's validity check tied to the live
     * item, so once the box has moved vanilla refuses clicks into it and closes it, and the
     * box item for other mods ({@link OpenItems.Box}).
     */
    private static final class BoxContainer extends StorageContainerAdapter implements OpenItems.Box {
        private final ServerPlayer player;
        private final Anchor anchor;

        BoxContainer(ServerPlayer player, Anchor anchor) {
            super(anchor);
            this.player = player;
            this.anchor = anchor;
        }

        @Override public boolean stillValid(Player p) { return stillOpen(player, anchor); }

        @Override public ItemStack stack() { return anchor.live.isStale() ? anchor.seen : anchor.live.stack(); }
    }

    private static <M extends AbstractContainerMenu> M ours(M menu) {
        OURS.add(menu);
        return menu;
    }

    /** Whether another player has the item at {@code path} in {@code top} on screen. */
    private static boolean openByAnother(ServerPlayer player, Container top, ItemPath path) {
        // By the top-level stack itself: two players at one large chest each have their own
        // container over the same two chests.
        ItemStack stack = top.getItem(path.slot());
        synchronized (STATES) {
            for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
                Front f = e.getValue().front;
                if (e.getKey() != player && f != null && f.menu == e.getKey().containerMenu && f.topStack == stack
                        && f.id.path() != null && path.inside().equals(f.id.path().inside())) return true;
            }
        }
        return false;
    }

    // What the other classes ask

    /** The item {@code player} has in front, the live stack itself (as last seen while it's off its place); empty for none. */
    static ItemStack frontItem(ServerPlayer player) {
        State st = STATES.get(player);
        Front f = st == null ? null : st.front;
        if (f == null || f.menu != player.containerMenu) return ItemStack.EMPTY;
        if (f.anchor != null) return f.anchor.live.isStale() ? f.anchor.seen : f.anchor.live.stack();
        return f.top.getItem(f.topSlot);
    }

    /** Which kind {@code player} has in front, or null. */
    static @Nullable Openable frontKind(ServerPlayer player) {
        State st = STATES.get(player);
        Front f = st == null ? null : st.front;
        return f == null || f.menu != player.containerMenu ? null : f.kind;
    }

    /**
     * Whether {@code container} holds the slots of an item someone has in front while it's
     * off its place (on the cursor, or just moved): they take nothing and give nothing then.
     */
    static boolean locked(Container container) {
        synchronized (STATES) {
            for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
                Front f = e.getValue().front;
                if (f == null || f.slots != container || f.menu != e.getKey().containerMenu) continue;
                if (f.frozen || offPlace(f) || !reachable(e.getKey(), f)) return true;
            }
        }
        return false;
    }
}
