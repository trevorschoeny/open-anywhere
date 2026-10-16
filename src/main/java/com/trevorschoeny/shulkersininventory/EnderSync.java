package com.trevorschoeny.shulkersininventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps each modded client's copy of its ender chest current.
 *
 * <p>Every server tick, each player's ender chest is compared with what was last sent,
 * and the whole chest goes out when anything differs. A new player object (first join,
 * respawn) has nothing sent yet, so it gets the full chest at once.
 *
 * <p>ponytail: a 27-slot compare per player per tick, no listeners. Trivial next to a
 * tick; switch to a container listener if a profile ever shows it.
 */
final class EnderSync {

    private EnderSync() {}

    private static final Map<ServerPlayer, List<ItemStack>> LAST_SENT = new WeakHashMap<>();

    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, EnderContentsS2C.TYPE)) continue;
            Container ender = player.getEnderChestInventory();
            List<ItemStack> sent = LAST_SENT.get(player);
            if (sent != null && sameContents(sent, ender)) continue;

            List<ItemStack> copy = new ArrayList<>(ender.getContainerSize());
            for (int i = 0; i < ender.getContainerSize(); i++) copy.add(ender.getItem(i).copy());
            LAST_SENT.put(player, copy);
            ServerPlayNetworking.send(player, new EnderContentsS2C(copy));
        }
    }

    private static boolean sameContents(List<ItemStack> sent, Container ender) {
        if (sent.size() != ender.getContainerSize()) return false;
        for (int i = 0; i < sent.size(); i++) {
            if (!ItemStack.matches(sent.get(i), ender.getItem(i))) return false;
        }
        return true;
    }
}
