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
 * An open bundle as both sides see it, for what its slots show and accept: the live bundle
 * on the server, the row's copy of it on the client, so the client predicts the server's answers.
 */
final class BundleView {

    private BundleView() {}

    /** The contents of the bundle {@code player} has open, or null with none open. */
    static @Nullable BundleContents contents(@Nullable Player player) {
        ItemStack bundle = Shown.item(player);
        if (Openable.of(bundle) != Openable.BUNDLE) return null;
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
