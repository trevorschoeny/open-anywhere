package com.trevorschoeny.shulkersininventory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;

/**
 * Whether the tooltip view is on for an item, here and now. Everything needs the server
 * to run the mod too (Trev, 2026-10-16: client + server only, no look-only mode), bundles
 * included, even though vanilla would let a client-only bundle view work.
 */
public final class ClientView {

    private ClientView() {}

    /**
     * True when the server runs this mod. Fabric only lets a client send a payload type
     * the server registered, so this is the check the design calls S1.
     */
    public static boolean serverHasMod() {
        return ClientPlayNetworking.canSend(SelectC2S.TYPE);
    }

    public static boolean shulkerGrid() { return ClientSettings.shulkerTooltip() && serverHasMod(); }
    public static boolean enderGrid()   { return ClientSettings.enderTooltip() && serverHasMod(); }
    public static boolean allBundleItems() { return ClientSettings.bundleTooltip() && serverHasMod(); }

    /** The grid this item shows in its tooltip, or false for vanilla's own tooltip. */
    public static boolean showsGrid(ItemStack stack) {
        return Openable.isShulker(stack) ? shulkerGrid() : Openable.isEnderChest(stack) && enderGrid();
    }
}
