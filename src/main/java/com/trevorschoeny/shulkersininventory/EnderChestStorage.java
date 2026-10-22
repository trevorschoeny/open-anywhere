package com.trevorschoeny.shulkersininventory;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The ender chest section's slots on the server: the player's own ender chest inventory,
 * read and written in place, so the section and every placed ender chest are the same 27
 * slots.
 */
final class EnderChestStorage implements Storage {

    private final Player player;

    EnderChestStorage(Player player) {
        this.player = player;
    }

    @Override
    public ItemStack getStack(int i) {
        return player.getEnderChestInventory().getItem(i);
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        player.getEnderChestInventory().setItem(i, stack);
    }

    @Override
    public int size() {
        return Openable.ENDER_CHEST.slots;
    }

    @Override
    public void markDirty() {
        player.getEnderChestInventory().setChanged();
    }
}
