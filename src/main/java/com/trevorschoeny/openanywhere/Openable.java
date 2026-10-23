package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * The three items this mod opens, and how big each one's slot group is.
 */
public enum Openable {

    SHULKER(27),
    ENDER_CHEST(27),
    /**
     * A bundle holds at most 64 stacks (64 single items of 64 kinds), shown one per
     * slot, plus one empty slot to put things in.
     */
    BUNDLE(65);

    /** How many slots each group declares. SlotKit compares this at join, so it never changes. */
    public final int slots;

    Openable(int slots) {
        this.slots = slots;
    }

    /** On the wire, by ordinal: both sides run the same build, so the order matches. */
    public static final StreamCodec<ByteBuf, Openable> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Openable::ordinal);

    /** What a stack opens as, or null for anything this mod doesn't open. */
    public static @Nullable Openable of(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return SHULKER;
        if (stack.is(Items.ENDER_CHEST)) return ENDER_CHEST;
        if (stack.getItem() instanceof BundleItem) return BUNDLE;
        return null;
    }
}
