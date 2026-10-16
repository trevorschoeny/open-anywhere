package com.trevorschoeny.shulkersininventory;

import net.minecraft.world.SimpleContainer;

/**
 * The client's copy of its own ender chest, filled by {@link EnderContentsS2C}. Lives in
 * the common source set only so {@link Openable#enderChest} can name it; nothing on the
 * server touches it.
 */
public final class EnderMirror {

    private EnderMirror() {}

    public static final SimpleContainer CLIENT = new SimpleContainer(Openable.GRID_SIZE);
}
