package com.trevorschoeny.shulkersininventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Which items this mod opens, and where an ender chest's contents live on each side. */
public final class Openable {

    private Openable() {}

    /** A shulker box holds 27 stacks, like a chest; so does the ender chest. */
    public static final int GRID_SIZE = 27;

    /**
     * Any shulker box item, dyed or not. Only ever a single one: with Carpet's stackable
     * shulkers a stack of boxes shares one contents list, and editing it would copy items
     * into every box (QuickShulker #68). Callers check the count.
     */
    public static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }

    /** An ender chest item. Holding one is what lets you reach your ender chest (design, Rule 1). */
    public static boolean isEnderChest(ItemStack stack) {
        return stack.is(Items.ENDER_CHEST);
    }

    /**
     * The player's ender chest as this side knows it. The server has the real one. The
     * client's own copy on the player is never filled by vanilla (and is replaced with the
     * player on every dimension change), so the client keeps the copy the server sends
     * in {@link EnderMirror}.
     */
    public static Container enderChest(Player player) {
        return player.level().isClientSide() ? EnderMirror.CLIENT : player.getEnderChestInventory();
    }
}
