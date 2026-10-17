package com.trevorschoeny.shulkersininventory;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Opening and closing items on the server, where every rule is checked. */
final class ServerSessions {

    private ServerSessions() {}

    /**
     * The player asked to open the item in {@code menuSlot} of their open menu. Opens it,
     * closes it if it's already the open one (shift+right-click again), or does nothing
     * when any rule says no. A refused request changes nothing, so a modified client
     * gains nothing by sending one.
     */
    static void open(ServerPlayer player, int menuSlot) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!opensOn(menu) || menuSlot < 0 || menuSlot >= menu.slots.size()) return;
        if (!menu.getCarried().isEmpty()) return;

        Slot slot = menu.getSlot(menuSlot);
        ItemStack stack = slot.getItem();
        Openable kind = Openable.of(stack);
        if (kind == null) return;
        // A stack of boxes (Carpet) shares one contents list: editing it would copy items
        // into every box (QuickShulker #68).
        if (kind == Openable.SHULKER && stack.getCount() != 1) return;
        // Open from a vanilla slot, or from the open ender chest's panel (a shulker kept
        // in the ender chest). Never from the shulker panel itself, whose backing is the
        // very item being swapped out.
        CreatedSlot created = CreatedSlot.of(slot);
        if (created != null && !created.panelId().equals(Openable.ENDER_CHEST.slotPanelId())) return;

        Session current = Sessions.get(player, kind);
        if (current != null && current.stack() == stack) {
            close(player, kind);
            return;
        }
        // One player per item: someone else looking into the same chest can't open the same box.
        if (Sessions.isOpenByAnyone(stack)) return;

        // Rule 2: one item at a time, plus the ender chest. Opening a shulker swaps out
        // the open shulker or bundle; opening the ender chest swaps out the ender chest.
        for (Session open : Sessions.of(player)) {
            if (open.kind().isEnderChest() == kind.isEnderChest()) close(player, open.kind());
        }
        Sessions.put(player, new Session(kind, menu, slot.container, slot.getContainerSlot(), menuSlot, stack));
        ServerPlayNetworking.send(player, new SessionS2C(kind, menuSlot));
    }

    static void close(ServerPlayer player, Openable kind) {
        if (Sessions.get(player, kind) == null) return;
        Sessions.remove(player, kind);
        ServerPlayNetworking.send(player, new SessionS2C(kind, -1));
    }

    /**
     * The guard SlotKit's slot lock can't be: every tick, each open item must still be
     * the same stack in the same place on the same open menu. A hopper under the chest,
     * another player, the chest broken, death, a dimension change, a command, another
     * mod: whatever moved it, the panel closes. Nothing is lost by closing, because
     * every change was already written into the item.
     */
    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Session s : Sessions.of(player)) {
                boolean valid = player.isAlive()
                        && player.containerMenu == s.menu()
                        && s.stillInPlace()
                        && (s.kind() != Openable.SHULKER || s.stack().getCount() == 1);
                if (!valid) close(player, s.kind());
            }
        }
    }

    /**
     * The screens items open on, for now (design, Rule 3): the player's inventory and
     * chest-like ones. A furnace or anvil draws the inventory into one fixed
     * background, so a panel there waits.
     */
    private static boolean opensOn(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu || menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }
}
