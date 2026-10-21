package com.trevorschoeny.shulkersininventory;

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
 * (including that ender chest), a box or a bundle opens in this mod's own slots below the
 * chest, which the client shows in MenuKit's container list (Trev, 2026-10-21). The
 * bundle on the inventory screen is its own panel.
 */
final class ServerSessions {

    private ServerSessions() {}

    /**
     * The player asked to open the item in {@code menuSlot} of their open menu. Opens it,
     * closes it if it's the open one (shift+right-click again), or does nothing when any
     * rule says no. A refused request changes nothing, so a modified client gains nothing
     * by sending one.
     */
    static void open(ServerPlayer player, int menuSlot) {
        // Survival only for now (design, Rule 4). The server checks too: a client's word
        // on its own mode isn't trusted.
        if (!ModeScope.SURVIVAL.includes(player)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menuSlot < 0 || menuSlot >= menu.slots.size() || !menu.getCarried().isEmpty()) return;

        Slot slot = menu.getSlot(menuSlot);
        ItemStack stack = slot.getItem();
        Openable kind = Openable.of(stack);
        if (kind == null) return;
        // A stack of boxes (Carpet) shares one contents list: editing it would copy items
        // into every box (QuickShulker #68).
        if (kind == Openable.SHULKER && stack.getCount() != 1) return;

        // Shift+right-click on the open item closes it.
        Session current = Sessions.get(player, kind);
        if (current != null && current.stack() == stack) {
            closeAndStepBack(player, kind);
            return;
        }
        // One player per item: two players can't have the same box open.
        if (Sessions.isOpenByAnyone(stack)) return;

        // On a chest's screen (a placed chest, a barrel, or the ender chest this mod
        // opened), a box or a bundle opens below the chest, in MenuKit's container list,
        // so both are on one screen (Trev, 2026-10-21). The bundle opens on the inventory
        // screen too, as its own panel.
        boolean onChest = menu instanceof ChestMenu;
        if (kind == Openable.BUNDLE) {
            if (!(menu instanceof InventoryMenu) && !onChest) return;
            openStacked(player, kind, menu, slot, menuSlot, stack);
            return;
        }
        if (kind == Openable.SHULKER && onChest) {
            openStacked(player, kind, menu, slot, menuSlot, stack);
            return;
        }
        // Natively from the inventory, or swapping from a screen this mod opened. The
        // ender chest from a placed chest's screen waits for its own section.
        if (!(menu instanceof InventoryMenu) && !isOpenNative(player, menu)) return;
        openNative(player, kind, slot.container, slot.getContainerSlot(), stack);
    }

    /** True when {@code menu} is one this mod opened natively (opening another from it swaps). */
    private static boolean isOpenNative(ServerPlayer player, AbstractContainerMenu menu) {
        for (Session s : Sessions.of(player)) if (s.menu() == menu && s.kind() != Openable.BUNDLE) return true;
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
        Sessions.put(player, new Session(kind, opened, container, containerSlot, menuSlot, stack));
        ServerPlayNetworking.send(player, SessionS2C.opened(kind, menuSlot));
    }

    /**
     * Opens a box or a bundle in this mod's own slots on the open menu: below the chest on
     * a chest's screen, or (the bundle) as a panel on the inventory screen. Only one such
     * item is open at a time; the ender chest the screen may be stays open (design, Rule 2).
     */
    private static void openStacked(ServerPlayer player, Openable kind, AbstractContainerMenu menu, Slot slot,
                                    int menuSlot, ItemStack stack) {
        for (Session s : Sessions.of(player)) if (s.stacked()) end(player, s);
        Sessions.put(player, new Session(kind, menu, slot.container, slot.getContainerSlot(), menuSlot, stack));
        // The client hears the session first, then the live count, then the contents.
        ServerPlayNetworking.send(player, SessionS2C.opened(kind, menuSlot));
        Groups.open(player, kind);
    }

    /** Ends a session without touching the screen: its slots close, the client hears. */
    private static void end(ServerPlayer player, Session s) {
        Sessions.remove(player, s.kind());
        if (s.stacked()) Groups.close(player, s.kind());
        ServerPlayNetworking.send(player, SessionS2C.closed(s.kind(), false));
    }

    /** Where a container slot sits on a menu, or -1 when the menu doesn't show it. */
    private static int slotOf(AbstractContainerMenu menu, Container container, int containerSlot) {
        for (Slot s : menu.slots) if (s.container == container && s.getContainerSlot() == containerSlot) return s.index;
        return -1;
    }

    /**
     * The close button, or shift+right-click on the open item: closes it. An item below a
     * chest closes alone, leaving the chest's screen as it was; one on vanilla's own screen
     * steps back to the inventory screen.
     */
    static void closeAndStepBack(ServerPlayer player, Openable kind) {
        Session session = Sessions.get(player, kind);
        if (session == null) return;
        if (session.stacked()) {
            end(player, session);
            return;
        }
        Sessions.remove(player, kind);
        player.closeContainer();
        ServerPlayNetworking.send(player, SessionS2C.closed(kind, true));
    }

    /**
     * The guard SlotKit's slot lock can't be: every tick, each open item must still be
     * the same stack in the same place, with its menu open. A hopper, another player,
     * death, a dimension change, a command, another mod: whatever moved it, the item
     * closes, and so does its screen. Nothing is lost by closing, because every change
     * was already written into the item. Closing the screen (Escape) ends it the same way.
     */
    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Session s : Sessions.of(player)) {
                boolean menuOpen = player.containerMenu == s.menu();
                boolean valid = player.isAlive() && menuOpen && s.stillInPlace()
                        && (s.kind() != Openable.SHULKER || s.stack().getCount() == 1);
                if (valid) continue;
                end(player, s);
                if (!s.stacked() && menuOpen) player.closeContainer();
            }
        }
    }
}
