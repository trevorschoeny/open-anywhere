package com.trevorschoeny.shulkersininventory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;

/**
 * Client init: the tooltip view's grid, and the ender chest copy the server sends. The
 * scroll-to-select half is {@link ContainerMouseActions}, added to every container
 * screen by {@code AddMouseActionsMixin}.
 */
public final class ShulkersInInventoryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTooltipComponentCallback.EVENT.register(data ->
                data instanceof ContainerTooltip grid ? new ClientContainerTooltip(grid) : null);

        ClientPlayNetworking.registerGlobalReceiver(EnderContentsS2C.TYPE, (payload, context) -> {
            for (int i = 0; i < EnderMirror.CLIENT.getContainerSize(); i++) {
                EnderMirror.CLIENT.setItem(i, i < payload.items().size() ? payload.items().get(i) : net.minecraft.world.item.ItemStack.EMPTY);
            }
        });
        // A copy from the last server must never show on the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EnderMirror.CLIENT.clearContent());
    }
}
