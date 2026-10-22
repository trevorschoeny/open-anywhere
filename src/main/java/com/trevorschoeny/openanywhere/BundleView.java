package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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

    /** True when {@code stack} is the same item, components included, as bundle stack {@code index}. */
    static boolean sameItem(BundleContents contents, int index, ItemStack stack) {
        return ItemStack.isSameItemSameComponents(contents.items().get(index).create(), stack);
    }

    /** How many of {@code stack} the bundle would take with stack {@code index} taken out. */
    static int fitWithout(BundleContents contents, int index, ItemStack stack) {
        List<ItemStackTemplate> rest = new ArrayList<>(contents.items());
        rest.remove(index);
        return new BundleContents(rest).asMutable().tryInsert(stack.copy());
    }
}
