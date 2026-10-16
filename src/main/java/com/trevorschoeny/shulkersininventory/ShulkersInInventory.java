package com.trevorschoeny.shulkersininventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Shulkers in Inventory: open a shulker box, a bundle or your ender chest without
 * placing it. Design: Inventory Suite drive, {@code mods/shulkers-in-inventory/design.md}.
 *
 * <p>Common init, both sides. It registers the two payloads and the server's halves of
 * the tooltip view: the selection the client sends while scrolling over an item, and
 * the ender chest copy the server keeps the client's tooltip filled with. The take
 * itself is {@code TakeSelectedMixin}. The container view (panels) is not built yet; it
 * waits on SlotKit K2/K3 and MenuKit K7 to K9.
 */
public final class ShulkersInInventory implements ModInitializer {

    public static final String MOD_ID = "shulkersininventory";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SelectC2S.TYPE, SelectC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EnderContentsS2C.TYPE, EnderContentsS2C.CODEC);

        // The client scrolled to a stack inside the item in a slot of its open menu (or
        // stopped hovering, index -1). Only remembered here; nothing moves until a click,
        // which checks the slot again (TakeSelectedMixin). Bounds are checked there too.
        ServerPlayNetworking.registerGlobalReceiver(SelectC2S.TYPE, (payload, context) ->
                Selection.set(context.player(), payload.slot(), payload.index()));

        ServerTickEvents.END_SERVER_TICK.register(EnderSync::tick);
    }
}
