package com.trevorschoeny.shulkersininventory;

import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * The open bundle as both sides see it, for what its slots show and accept: read from
 * the bundle in the source slot now, so the client predicts the server's answers.
 */
final class BundleView {

    private BundleView() {}

    /** The open bundle's contents, or null with no bundle open for this player. */
    static @Nullable BundleContents contents(@Nullable Player player) {
        if (player == null) return null;
        Session session = Sessions.get(player, Openable.BUNDLE);
        if (session == null) return null;
        ItemStack bundle = session.container().getItem(session.containerSlot());
        return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    /**
     * Whether bundle slot {@code index} shows: every filled one, plus the next one to add
     * to, unless the bundle is full (64 weight, or 64 stacks).
     */
    static boolean shows(@Nullable BundleContents contents, int index) {
        if (contents == null) return false;
        if (index < contents.size()) return true;
        return index == contents.size() && !full(contents);
    }

    /** The slot that adds to the bundle (the plus), or -1 when it's full. */
    static int addSlot(@Nullable BundleContents contents) {
        return contents == null || full(contents) ? -1 : contents.size();
    }

    private static boolean full(BundleContents contents) {
        return contents.size() >= Openable.BUNDLE.slots - 1
                || contents.weight().result().map(w -> w.compareTo(Fraction.ONE) >= 0).orElse(true);
    }

    /** How many of {@code stack} the bundle would take, by vanilla's own rule (a dry run). */
    static int fit(BundleContents contents, ItemStack stack) {
        return contents.asMutable().tryInsert(stack.copy());
    }
}
