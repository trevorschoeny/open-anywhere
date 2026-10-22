package com.trevorschoeny.openanywhere;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.storage.StorageContainerAdapter;


import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Opening and closing items on the server, where every rule is checked.
 *
 * <p>From the inventory, a shulker box or the ender chest opens natively: the server
 * opens vanilla's own shulker box or ender chest menu, so the client shows vanilla's own
 * screen, exactly as a placed one opens (Trev, 2026-10-16). On a chest's screen
 * (including that ender chest), a box, a bundle or the ender chest opens in one of this
 * mod's lanes below the chest, which the client shows in MenuKit's container list (Trev,
 * 2026-10-21). From the inventory the bundle opens on its own host screen
 * ({@link BundleHost}), the same way. Any number open at once (Trev, 2026-10-21), up to
 * the lanes there are.
 */
final class ServerSessions {

    private ServerSessions() {}

    /**
     * The player asked to open the item in {@code menuSlot} of their open menu. Opens it,
     * closes it if it's open (double-clicked again), or does nothing when any rule says no.
     * A refused request changes nothing, so a modified client gains nothing by sending one.
     *
     * <p>{@code fromCursor}: a double-click's first click was vanilla's, and lifted the item
     * onto the cursor (Trev, 2026-10-22: the brief lift is accepted). It goes back into its
     * now-empty slot first, through the slot's own rules, then opens as if never lifted. If
     * it can't go back, nothing happens: the item stays on the cursor, as after one click.
     */
    static void open(ServerPlayer player, int menuSlot, boolean fromCursor) {
        // Survival only for now (design, Rule 4). The server checks too: a client's word
        // on its own mode isn't trusted.
        if (!ModeScope.SURVIVAL.includes(player)) return;
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

        Slot slot = menu.getSlot(menuSlot);
        // Nothing opens from inside an opened box or bundle (design, Rule 3): the inner item
        // lives in the outer one's contents, so it couldn't stay open once that one closes.
        if (Groups.isOpenedContents(slot)) return;
        ItemStack stack = slot.getItem();
        Openable kind = Openable.of(stack);
        if (kind == null) return;
        // A stack of boxes (Carpet) shares one contents list: editing it would copy items
        // into every box (QuickShulker #68).
        if (kind == Openable.SHULKER && stack.getCount() != 1) return;

        // Double-clicking an open item closes it.
        Session current = Sessions.byStack(player, stack);
        if (current != null) {
            closeAndStepBack(player, current);
            return;
        }
        // One player per item: two players can't have the same box open.
        if (Sessions.isOpenByAnyone(stack)) return;

        // On a chest's screen (a placed chest, a barrel, or the ender chest this mod
        // opened), an item opens below the chest, in MenuKit's container list, so all are
        // on one screen (Trev, 2026-10-21). The bundle's own host screen isn't a chest's:
        // from it, a box or the ender chest swaps, as from vanilla's.
        boolean host = BundleHost.is(menu);
        boolean onChest = menu instanceof ChestMenu && !host;
        if (kind == Openable.BUNDLE) {
            if (menu instanceof InventoryMenu) {
                openHosted(player, slot.container, slot.getContainerSlot(), stack);
            } else if (onChest || host) {
                openStacked(player, kind, menu, slot, menuSlot, stack);
            }
            return;
        }
        // At a placed ender chest the ender chest is already the screen: nothing to add.
        if (kind == Openable.ENDER_CHEST && onChest
                && ((ChestMenu) menu).getContainer() == player.getEnderChestInventory()) return;
        // A box, or the ender chest, on a chest's screen: a section. On vanilla's ender
        // chest screen this mod opened, a box opens below it too.
        if (onChest && (kind == Openable.SHULKER || !isOpenNative(player, menu))) {
            openStacked(player, kind, menu, slot, menuSlot, stack);
            return;
        }
        // Natively from the inventory, or swapping from a screen this mod opened.
        if (!(menu instanceof InventoryMenu) && !host && !isOpenNative(player, menu)) return;
        openNative(player, kind, slot.container, slot.getContainerSlot(), stack);
    }

    /** True when {@code menu} is one this mod opened natively (opening another from it swaps). */
    private static boolean isOpenNative(ServerPlayer player, AbstractContainerMenu menu) {
        for (Session s : Sessions.of(player)) if (s.menu() == menu && !s.stacked()) return true;
        return false;
    }

    /**
     * Opens vanilla's menu for a shulker box or the ender chest. Vanilla closes whatever
     * menu was open first, so the sessions on it end; the new session finds the opened
     * item's slot on the new menu, for the client's lock and grey.
     */
    private static void openNative(ServerPlayer player, Openable kind, Container container, int containerSlot, ItemStack stack) {
        for (Session s : Sessions.of(player)) end(player, s);

        if (kind == Openable.ENDER_CHEST) {
            // A real ender chest elsewhere may still be set as the active one; its
            // distance check would close this menu at once.
            player.getEnderChestInventory().setActiveChest(null);
            player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()),
                    Component.translatable("container.enderchest")));
        } else {
            Container contents = new StorageContainerAdapter(new ShulkerStorage(stack));
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShulkerBoxMenu(id, inventory, contents),
                    stack.getHoverName()));
        }

        AbstractContainerMenu opened = player.containerMenu;
        int menuSlot = slotOf(opened, container, containerSlot);
        Sessions.put(player, new Session(kind, -1, opened, container, containerSlot, menuSlot, stack));
        ServerPlayNetworking.send(player, SessionS2C.opened(kind, -1, menuSlot));
    }

    /**
     * The bundle from the inventory: opens its host screen, vanilla's chest with no rows
     * ({@link BundleHost}), then the bundle in a lane on it, as below a chest.
     */
    private static void openHosted(ServerPlayer player, Container container, int containerSlot, ItemStack stack) {
        for (Session s : Sessions.of(player)) end(player, s);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> BundleHost.create(id, inventory), stack.getHoverName()));
        AbstractContainerMenu opened = player.containerMenu;
        if (!BundleHost.is(opened)) return;
        int menuSlot = slotOf(opened, container, containerSlot);
        if (menuSlot < 0) return;
        openStacked(player, Openable.BUNDLE, opened, opened.getSlot(menuSlot), menuSlot, stack);
    }

    /**
     * Opens an item in a free lane of its kind on the open menu, beside whatever else is
     * open (design, Rule 2). With every lane of its kind taken, nothing opens. The ender
     * chest has one lane, and another ender chest item takes it over.
     */
    private static void openStacked(ServerPlayer player, Openable kind, AbstractContainerMenu menu, Slot slot,
                                    int menuSlot, ItemStack stack) {
        int lane = kind == Openable.ENDER_CHEST ? 0 : freeLane(player, kind);
        if (lane < 0) return;
        Session old = Sessions.at(player, kind, lane);
        if (old != null) end(player, old);
        Sessions.put(player, new Session(kind, lane, menu, slot.container, slot.getContainerSlot(), menuSlot, stack));
        // The client hears the session first, then the live count, then the contents.
        ServerPlayNetworking.send(player, SessionS2C.opened(kind, lane, menuSlot));
        Groups.open(player, kind, lane);
    }

    /** The lowest lane of {@code kind} with nothing open in it, or -1. */
    private static int freeLane(ServerPlayer player, Openable kind) {
        for (int lane = 0; lane < kind.lanes; lane++) if (Sessions.at(player, kind, lane) == null) return lane;
        return -1;
    }

    /** Ends a session without touching the screen: its slots close, the client hears. */
    private static void end(ServerPlayer player, Session s) {
        // An item opened from inside the ender chest's section goes with it: its slot is
        // about to stop showing.
        if (s.kind() == Openable.ENDER_CHEST && s.stacked()) {
            for (Session inner : Sessions.of(player)) {
                if (inner == s || !inner.stacked() || inner.menuSlot() < 0 || inner.menuSlot() >= inner.menu().slots.size()) continue;
                if (Groups.isIn(inner.menu().getSlot(inner.menuSlot()), Openable.ENDER_CHEST)) end(player, inner);
            }
        }
        Sessions.remove(player, s.kind(), s.lane());
        if (s.stacked()) Groups.close(player, s.kind(), s.lane());
        ServerPlayNetworking.send(player, SessionS2C.closed(s.kind(), s.lane(), false));
    }

    /** Where a container slot sits on a menu, or -1 when the menu doesn't show it. */
    private static int slotOf(AbstractContainerMenu menu, Container container, int containerSlot) {
        for (Slot s : menu.slots) if (s.container == container && s.getContainerSlot() == containerSlot) return s.index;
        return -1;
    }

    /** A close button: the session in that lane (-1: vanilla's own screen). */
    static void close(ServerPlayer player, Openable kind, int lane) {
        Session session = Sessions.at(player, kind, lane);
        if (session != null) closeAndStepBack(player, session);
    }

    /**
     * Closes one opened item. A section closes alone, leaving the screen as it was; one on
     * vanilla's own screen, or the last one on the bundle's host, steps back to the
     * inventory screen.
     */
    static void closeAndStepBack(ServerPlayer player, Session session) {
        boolean last = !session.stacked() || (BundleHost.is(session.menu()) && othersOn(player, session) == 0);
        if (!last) {
            end(player, session);
            return;
        }
        Sessions.remove(player, session.kind(), session.lane());
        if (session.stacked()) Groups.close(player, session.kind(), session.lane());
        player.closeContainer();
        ServerPlayNetworking.send(player, SessionS2C.closed(session.kind(), session.lane(), true));
    }

    /** How many other sections are open on {@code session}'s menu. */
    private static int othersOn(ServerPlayer player, Session session) {
        int n = 0;
        for (Session s : Sessions.of(player)) if (s != session && s.stacked() && s.menu() == session.menu()) n++;
        return n;
    }

    /**
     * The guard SlotKit's slot lock can't be: every tick, each open item must still be
     * the same stack in the same place, with its menu open. A hopper, another player,
     * death, a dimension change, a command, another mod: whatever moved it, the item
     * closes, and so does a screen with nothing left on it. Nothing is lost by closing,
     * because every change was already written into the item. Closing the screen
     * (Escape) ends them all the same way.
     */
    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Session s : Sessions.of(player)) {
                if (Sessions.at(player, s.kind(), s.lane()) != s) continue; // ended with the ender chest
                boolean menuOpen = player.containerMenu == s.menu();
                boolean valid = player.isAlive() && menuOpen && s.stillInPlace()
                        && (s.kind() != Openable.SHULKER || s.stack().getCount() == 1);
                if (valid) continue;
                boolean emptyHost = BundleHost.is(s.menu()) && othersOn(player, s) == 0;
                end(player, s);
                if ((!s.stacked() || emptyHost) && menuOpen) player.closeContainer();
            }
        }
    }
}
