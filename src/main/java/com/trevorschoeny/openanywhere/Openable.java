package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * The three items this mod opens: how big each one's slot group is, and how many can be
 * open at once (its lanes, one slot group each).
 */
public enum Openable {

    // ponytail: fixed lanes, because SlotKit groups are registered at start and sit on
    // every menu. Six boxes and three bundles at once is the ceiling; a group SlotKit adds
    // to an open menu on demand would lift it.
    SHULKER(27, 6),
    /** One: there is one ender chest per player. */
    ENDER_CHEST(27, 1),
    /**
     * A bundle holds at most 64 stacks (64 single items of 64 kinds), shown one per
     * slot, plus one empty slot to put things in.
     */
    BUNDLE(65, 3);

    /** How many slots each group declares. SlotKit compares this at join, so it never changes. */
    public final int slots;
    /** How many of this kind can be open as sections at once. */
    public final int lanes;

    Openable(int slots, int lanes) {
        this.slots = slots;
        this.lanes = lanes;
    }

    /** Lane {@code lane}'s group owner and section id: {@code openanywhere:shulker_0}, and so on. */
    public String panelId(int lane) {
        return OpenAnywhere.MOD_ID + ":" + name().toLowerCase(java.util.Locale.ROOT) + "_" + lane;
    }

    /**
     * The id SlotKit gives lane {@code lane}'s built slots ({@code CreatedSlot.panelId()}):
     * the owner and the group, {@code openanywhere:shulker_0:slots}.
     */
    public String slotPanelId(int lane) {
        return panelId(lane) + ":" + Groups.GROUP;
    }

    /** What a stack opens as, or null for anything this mod doesn't open. */
    public static @Nullable Openable of(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return SHULKER;
        if (stack.is(Items.ENDER_CHEST)) return ENDER_CHEST;
        if (stack.getItem() instanceof BundleItem) return BUNDLE;
        return null;
    }
}
