package com.trevorschoeny.shulkersininventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Shulkers in Inventory: open a shulker box, a bundle or your ender chest as a panel on
 * the screen you're on, without placing it. Design: Inventory Suite drive,
 * {@code mods/shulkers-in-inventory/design.md}.
 *
 * <p>Common init, both sides: the slot groups, the slot lock and the shift-click routes ({@link Groups}), the three
 * payloads, and the server's open, close and per-tick check ({@link ServerSessions}).
 */
public final class ShulkersInInventory implements ModInitializer {

    public static final String MOD_ID = "shulkersininventory";

    @Override
    public void onInitialize() {
        Groups.register();

        PayloadTypeRegistry.serverboundPlay().register(OpenC2S.TYPE, OpenC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseC2S.TYPE, CloseC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SessionS2C.TYPE, SessionS2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenC2S.TYPE,
                (payload, context) -> ServerSessions.open(context.player(), payload.menuSlot()));
        ServerPlayNetworking.registerGlobalReceiver(CloseC2S.TYPE,
                (payload, context) -> ServerSessions.closeAndStepBack(context.player(), payload.kind()));
        ServerTickEvents.END_SERVER_TICK.register(ServerSessions::tick);
    }
}
