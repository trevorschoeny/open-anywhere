package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /**
     * An item the player opened by hand: where it sits, the top-level stack it's in (by
     * identity), and a copy of it as last seen there, to know it again once it moves.
     */
    private record Mark(ItemPath path, ItemStack top, ItemStack seen) {}

    /**
     * An item opened by hand, now on the cursor, its tab closed: it opens again where it's
     * put down, and in front again if it was in front ({@code front}). Gone when it leaves the
     * cursor any other way, or when the screens close.
     */
    private record Lifted(ItemPath path, ItemStack item, boolean front) {}

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
        final List<Mark> marks = new ArrayList<>();
        final List<Lifted> lifted = new ArrayList<>();
        @Nullable Front front;
        List<Tab> sentRow = List.of();
        @Nullable TabId sentShown = TabId.INVENTORY;
        boolean sentFrozen;
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

    /** One item on the row: where it sits, and a copy of it. */
    private record Item(ItemPath path, ItemStack stack) {}

    /** The player's row, in order: the inventory, the world container, then the items. */
    private static List<Tab> row(ServerPlayer player, State st) {
        AbstractContainerMenu world = world(player);
        List<Tab> row = new ArrayList<>();
        row.add(Tab.of(TabId.INVENTORY));
        if (world != null) row.add(Tab.of(TabId.WORLD));
        List<Item> items = st.prefs.everyBox() ? everything(player, world, st.prefs) : marked(player, world, st);
        // The item in front, on the cursor, keeps its tab where it was.
        Front f = st.front;
        if (f != null && f.frozen && items.stream().noneMatch(i -> i.path().equals(f.id.path()))) items.add(new Item(f.id.path(), f.seen));
        items.sort(Comparator.comparing(Item::path, ORDER));
        // One ender chest tab, however many ender chests the player carries: the first one's.
        boolean ender = worldIsEnderChest(player, world);
        for (Item item : items) {
            if (Openable.of(item.stack()) == Openable.ENDER_CHEST) {
                if (ender) continue;
                ender = true;
            }
            row.add(new Tab(TabId.item(item.path()), item.stack()));
        }
        return row;
    }

    /**
     * The items the player opened by hand. A mark whose item moved follows it there (a
     * shift-click, a lift and put-down in one tick); one whose item is on the cursor closes
     * and waits there ({@link Lifted}, {@link #settle}); any other is gone.
     */
    private static List<Item> marked(ServerPlayer player, @Nullable AbstractContainerMenu world, State st) {
        ItemStack carried = player.containerMenu.getCarried();
        List<Mark> kept = new ArrayList<>();
        for (Mark m : st.marks) {
            if (stillThere(player, world, m)) {
                kept.add(new Mark(m.path(), m.top(), at(player, world, m.path()).copy()));
            } else if (m.path().depth() == 0 && ItemStack.matches(carried, m.seen())) {
                if (st.lifted.stream().noneMatch(l -> l.path().equals(m.path()))) st.lifted.add(new Lifted(m.path(), m.seen(), false));
            } else {
                ItemPath to = find(player, world, m.seen(), null);
                if (to != null && kept.stream().noneMatch(k -> k.path().equals(to)))
                    kept.add(new Mark(to, topOf(player, world, to).getItem(to.slot()), m.seen()));
            }
        }
        st.marks.clear();
        st.marks.addAll(kept);
        List<Item> items = new ArrayList<>();
        for (Mark m : st.marks) items.add(new Item(m.path(), m.seen()));
        return items;
    }

    /**
     * Where {@code stack} is now, at the top level: {@code prefer} if it's there, else the
     * first slot holding the same item in the inventory, the world container or the ender
     * chest. Null when it's nowhere in reach.
     * ponytail: an identical item elsewhere (two empty red boxes) answers just as well; the
     * contents are the same, only the tab's place differs.
     */
    private static @Nullable ItemPath find(ServerPlayer player, @Nullable AbstractContainerMenu world, ItemStack stack, @Nullable ItemPath prefer) {
        if (stack.isEmpty()) return null;
        if (prefer != null) {
            ItemStack there = at(player, world, prefer);
            if (there != null && ItemStack.matches(there, stack)) return prefer;
        }
        for (int i = 0; i < 36; i++) {
            if (ItemStack.matches(player.getInventory().getItem(i), stack)) return new ItemPath(ItemRef.Source.INVENTORY, i, List.of());
        }
        if (world != null && !worldIsEnderChest(player, world)) {
            Container c = containerOf(world);
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (ItemStack.matches(c.getItem(i), stack)) return new ItemPath(ItemRef.Source.CONTAINER, i, List.of());
            }
        }
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (ItemStack.matches(ender.getItem(i), stack)) return new ItemPath(ItemRef.Source.ENDER_CHEST, i, List.of());
        }
        return null;
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
        for (ItemSources.Found f : found) items.add(new Item(f.path(), f.stack()));
        return items;
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
        boolean inPlace = f.anchor != null ? !f.anchor.live.isStale()
                : f.top.getItem(f.topSlot) == f.topStack && at(player, world, path) != null;
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
            st.marks.removeIf(m -> m.path().equals(path));
            st.lifted.add(new Lifted(path, f.seen.copy(), true));
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
        if (top == null) return false;
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
        ItemPath from = f.id.path();
        ItemStack topStack = top.getItem(to.slot());
        st.marks.replaceAll(m -> m.path().equals(from) ? new Mark(to, topStack, m.seen()) : m);
        f.id = TabId.item(to);
        f.top = top;
        f.topSlot = to.slot();
        f.topStack = topStack;
        f.frozen = false;
        player.containerMenu.broadcastChanges();
        return true;
    }

    /**
     * Opened by hand, then lifted: put down anywhere in reach, it opens again there, and comes
     * back in front if it was in front. Leaving the cursor any other way, it stays closed.
     */
    private static void settle(ServerPlayer player, State st) {
        if (st.lifted.isEmpty()) return;
        ItemStack carried = player.containerMenu.getCarried();
        AbstractContainerMenu world = world(player);
        for (Lifted l : List.copyOf(st.lifted)) {
            if (ItemStack.matches(carried, l.item())) continue;
            st.lifted.remove(l);
            ItemPath to = find(player, world, l.item(), l.path());
            Container top = to == null ? null : topOf(player, world, to);
            if (top == null) continue;
            if (st.marks.stream().noneMatch(m -> m.path().equals(to))) st.marks.add(new Mark(to, top.getItem(to.slot()), l.item()));
            if (l.front()) pick(player, TabId.item(to));
        }
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

    /** The player went back to the world: what they opened by hand closes with the screens. */
    static void done(ServerPlayer player) {
        State st = state(player);
        st.marks.clear();
        st.front = null;
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
                menu.setCarried(ItemStack.EMPTY);
                st.lifted.remove(l);
                menu.broadcastChanges();
                sync(player, st);
                return;
            }
        }
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
            // Every box has a tab: a double-click shows it, and on the one in front, closes it,
            // back to its group's screen.
            TabId tab = tabFor(player, st, path, kind);
            if (tab.equals(shown(player, st))) goHome(player, st, path);
            else pick(player, tab);
            sync(player, st);
            return;
        }
        Mark mark = markFor(st, player, world, path, kind);
        if (mark != null) {
            st.marks.remove(mark);
            if (st.front != null && st.front.id.equals(TabId.item(mark.path()))) goHome(player, st, mark.path());
            sync(player, st);
            return;
        }
        Container top = topOf(player, world, path);
        if (top == null) return;
        st.marks.add(new Mark(path, top.getItem(path.slot()), stack.copy()));
        pick(player, TabId.item(path));
    }

    /** The close button on item tab {@code id}: it closes, and its screen gives way if it's in front. */
    static void close(ServerPlayer player, TabId id) {
        State st = state(player);
        if (id.path() == null) return;
        st.marks.removeIf(m -> m.path().equals(id.path()));
        if (st.front != null && st.front.id.equals(id)) goHome(player, st, id.path());
        sync(player, st);
    }

    /**
     * Off the item in front, to its group's own screen: the inventory for what's in the
     * inventory, the world container for what's in it, the ender chest for what's in the
     * ender chest. The inventory when that screen isn't there.
     */
    private static void goHome(ServerPlayer player, State st, @Nullable ItemPath path) {
        AbstractContainerMenu world = world(player);
        st.front = null;
        ItemRef.Source source = path == null ? ItemRef.Source.INVENTORY : path.source();
        if (source == ItemRef.Source.CONTAINER && world != null
                || source == ItemRef.Source.ENDER_CHEST && worldIsEnderChest(player, world)) {
            MenuSwitch.back(player);
            return;
        }
        if (source == ItemRef.Source.ENDER_CHEST) {
            for (Tab tab : row(player, st)) {
                if (tab.id().path() != null && Openable.of(tab.item()) == Openable.ENDER_CHEST) {
                    openItem(player, st, tab.id().path());
                    return;
                }
            }
        }
        MenuSwitch.openInventory(player);
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
            Front f = show(player, st, ender, path, kind, null, top, item.copy());
            if (f != null) f.slots = player.getEnderChestInventory();
            return;
        }
        if (openByAnother(player, top, path)) return;
        LiveItem live = path.source() == ItemRef.Source.CONTAINER ? ItemSources.live(top, path) : ItemSources.live(player, path);
        if (live == null) return;
        Component title = live.stack().getHoverName();
        if (kind == Openable.SHULKER) {
            int size = OpenItems.boxSizeOf(live.stack());
            Anchor anchor = new Anchor(live, live.storage(new ShulkerStorage(live.stack(), size)), size,
                    seen -> new ShulkerStorage(seen, size));
            BoxContainer box = new BoxContainer(player, anchor);
            Front f = show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(new ShulkerBoxMenu(id, inventory, box)), title),
                    path, kind, anchor, top, live.stack().copy());
            if (f != null) f.slots = box;
            return;
        }
        BundleStorage contents = new BundleStorage(live.stack());
        Anchor anchor = new Anchor(live, live.storage(contents), Openable.BUNDLE.slots, BundleStorage::new);
        Container validity = new SimpleContainer(0) {
            @Override public boolean stillValid(Player p) { return stillOpen(player, anchor); }
        };
        Front f = show(player, st, new SimpleMenuProvider((id, inventory, p) -> ours(ContainerHost.create(id, inventory, validity)), title),
                path, kind, anchor, top, live.stack().copy());
        if (f == null) return;
        f.bundle = Groups.addBundle(player, anchor, contents);
        if (f.bundle != null && !f.bundle.slots().isEmpty()) f.slots = f.bundle.slots().getFirst().container;
        player.containerMenu.broadcastChanges();
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

    /**
     * Whether the item behind {@code anchor} still has its screen: in place, on the cursor,
     * or moved within reach (the tick then follows it). Vanilla closes the screen when not.
     */
    private static boolean stillOpen(ServerPlayer player, Anchor anchor) {
        if (!anchor.live.isStale()) return true;
        if (ItemStack.matches(player.containerMenu.getCarried(), anchor.seen)) return true;
        return find(player, world(player), anchor.seen, null) != null;
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
        synchronized (STATES) {
            for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
                Front f = e.getValue().front;
                if (e.getKey() != player && f != null && f.menu == e.getKey().containerMenu && f.top == top
                        && path.equals(f.id.path())) return true;
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
                if (f.frozen || f.anchor != null && f.anchor.live.isStale() || f.anchor == null && f.top.getItem(f.topSlot) != f.topStack)
                    return true;
            }
        }
        return false;
    }
}
