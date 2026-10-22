package com.trevorschoeny.openanywhere;



import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Opening and closing items on the server, where every rule is checked.
 *
 * <p>Every opened item is a tab in MenuKit's container list (Trev, 2026-10-22): on a
 * chest's screen beside the chest, and from the inventory on the host screen
 * ({@link ContainerHost}), vanilla's chest screen with no rows of its own. Any number open
 * at once, up to the lanes there are.
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
        // Any game mode (Trev, 2026-10-22): in creative, opening from the inventory opens the
        // container's own screen, as in survival, so creative's tabs give way to it.
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
        // Off on this server: nothing opens (a double-clicked item is already back in its slot).
        if (kind == null || !ServerSettings.allows(kind)) return;
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

        // From the inventory, anything opens on the host screen, as a tab (Trev, 2026-10-22);
        // on the host, or on a chest's screen (a placed chest, a barrel), it opens as another
        // tab beside what's there.
        boolean host = ContainerHost.is(menu);
        if (menu instanceof InventoryMenu) {
            openHosted(player, kind, slot.container, slot.getContainerSlot(), stack);
            return;
        }
        if (!(menu instanceof ChestMenu)) return;
        // At a placed ender chest the ender chest is already the screen: nothing to add.
        if (kind == Openable.ENDER_CHEST && !host
                && ((ChestMenu) menu).getContainer() == player.getEnderChestInventory()) return;
        openStacked(player, kind, menu, slot, menuSlot, stack);
    }

    /**
     * An item from the inventory: opens the host screen, vanilla's chest with no rows
     * ({@link ContainerHost}), then the item as a tab on it, as on a chest.
     */
    private static void openHosted(ServerPlayer player, Openable kind, Container container, int containerSlot, ItemStack stack) {
        for (Session s : Sessions.of(player)) end(player, s);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ContainerHost.create(id, inventory), stack.getHoverName()));
        AbstractContainerMenu opened = player.containerMenu;
        if (!ContainerHost.is(opened)) return;
        int menuSlot = slotOf(opened, container, containerSlot);
        if (menuSlot < 0) return;
        openStacked(player, kind, opened, opened.getSlot(menuSlot), menuSlot, stack);
    }

    /**
     * Opens an item in a free lane of its kind on the open menu, beside whatever else is
     * open (design, Rule 2). With every lane of its kind taken, nothing opens. The ender
     * chest has one lane, and another ender chest item takes it over.
     */
    private static void openStacked(ServerPlayer player, Openable kind, AbstractContainerMenu menu, Slot slot,
                                    int menuSlot, ItemStack stack) {
        int lane = kind == Openable.ENDER_CHEST ? 0 : freeLane(player, kind);
        if (lane < 0) {
            // ponytail: a fixed number of lanes (12 boxes, 3 bundles); goes when SlotKit adds
            // a group on demand. Until then, say why nothing opened, above the hotbar.
            player.sendOverlayMessage(Component.translatable("openanywhere.too_many"));
            return;
        }
        Session old = Sessions.at(player, kind, lane);
        if (old != null) end(player, old);
        Sessions.put(player, new Session(kind, lane, menu, slot.container, slot.getContainerSlot(), menuSlot, stack));
        // The live count and the contents go first, the session last, so the client has the
        // box's slots filled the moment it hears it opened: its tab shows full on the first
        // frame, with no frame of the box greyed in the chest or of an empty grid.
        Groups.open(player, kind, lane);
        menu.broadcastChanges();
        ServerPlayNetworking.send(player, SessionS2C.opened(kind, lane, menuSlot));
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
        boolean last = !session.stacked() || (ContainerHost.is(session.menu()) && othersOn(player, session) == 0);
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
                boolean emptyHost = ContainerHost.is(s.menu()) && othersOn(player, s) == 0;
                end(player, s);
                if ((!s.stacked() || emptyHost) && menuOpen) player.closeContainer();
            }
        }
    }
}
