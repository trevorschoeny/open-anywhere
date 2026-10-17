package com.trevorschoeny.shulkersininventory;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** The three items this mod opens, and how big each one's panel is. */
public enum Openable {

    SHULKER(27),
    ENDER_CHEST(27),
    /**
     * A bundle holds at most 64 stacks (64 single items of 64 kinds), shown one per
     * slot, plus one empty slot to put things in.
     */
    BUNDLE(65);

    /** How many slots the panel declares. SlotKit compares this at join, so it never changes. */
    public final int slots;

    Openable(int slots) {
        this.slots = slots;
    }

    /** The panel this item opens in: {@code shulkersininventory:shulker}, and so on. */
    public String panelId() {
        return ShulkersInInventory.MOD_ID + ":" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The id SlotKit gives this kind's built slots ({@code CreatedSlot.panelId()}): the
     * owner and the group, {@code shulkersininventory:shulker:slots}.
     */
    public String slotPanelId() {
        return panelId() + ":" + Groups.GROUP;
    }

    /** Whether this is the one that may stay open beside another (design, Rule 2). */
    public boolean isEnderChest() {
        return this == ENDER_CHEST;
    }

    /** What a stack opens as, or null for anything this mod doesn't open. */
    public static @Nullable Openable of(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return SHULKER;
        if (stack.is(Items.ENDER_CHEST)) return ENDER_CHEST;
        if (stack.getItem() instanceof BundleItem) return BUNDLE;
        return null;
    }
}
