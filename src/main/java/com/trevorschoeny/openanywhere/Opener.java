package com.trevorschoeny.openanywhere;

import net.minecraft.world.item.ItemStack;

/**
 * What the player is using while a screen might open: the block they clicked or the entity
 * they interacted with, as an item. Set around vanilla's own use calls (the Use mixins) and
 * read when a menu opens, for the world tab's icon ({@link ServerTabs#opened}). Server thread only.
 */
public final class Opener {

    private Opener() {}

    private static ItemStack using = ItemStack.EMPTY;

    /** The player starts using {@code icon}'s block or entity; {@code ItemStack.EMPTY} when the use is over. */
    public static void using(ItemStack icon) {
        using = icon;
    }

    static ItemStack using() {
        return using;
    }
}
