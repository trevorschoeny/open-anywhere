package com.trevorschoeny.shulkersininventory;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The ender chest panel's slots on the server: the player's own ender chest, only while
 * one is open. Vanilla's container already hands out its stacks live, so this passes
 * straight through.
 */
final class EnderStorage implements Storage {

    private final Player player;

    EnderStorage(Player player) {
        this.player = player;
    }

    private boolean open() {
        return Sessions.get(player, Openable.ENDER_CHEST) != null;
    }

    @Override
    public ItemStack getStack(int i) {
        return open() ? player.getEnderChestInventory().getItem(i) : ItemStack.EMPTY;
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        if (open()) player.getEnderChestInventory().setItem(i, stack);
    }

    @Override
    public int size() {
        return Openable.ENDER_CHEST.slots;
    }

    @Override
    public void markDirty() {
        if (open()) player.getEnderChestInventory().setChanged();
    }
}
