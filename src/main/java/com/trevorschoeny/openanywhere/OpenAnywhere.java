package com.trevorschoeny.openanywhere;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Open Anywhere: open a shulker box, a bundle or your ender chest from your inventory or an
 * open chest, without placing it, each on its own screen, picked by tabs.
 *
 * <p>Common init, both sides: the bundle's slots, the slot lock and the host's shift-click
 * ({@link Groups}), the payloads, and the server's tabs ({@link ServerTabs}).
 */
public final class OpenAnywhere implements ModInitializer {

    public static final String MOD_ID = "openanywhere";

    @Override
    public void onInitialize() {
        Groups.register();
        ContainerHost.register();

        PayloadTypeRegistry.serverboundPlay().register(OpenC2S.TYPE, OpenC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseC2S.TYPE, CloseC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PickC2S.TYPE, PickC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PrefsC2S.TYPE, PrefsC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DoneC2S.TYPE, DoneC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TabsS2C.TYPE, TabsS2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenC2S.TYPE,
                (payload, context) -> ServerTabs.open(context.player(), payload.menuSlot(), payload.fromCursor()));
        ServerPlayNetworking.registerGlobalReceiver(CloseC2S.TYPE, (payload, context) -> ServerTabs.close(context.player(), payload.id()));
        ServerPlayNetworking.registerGlobalReceiver(PickC2S.TYPE, (payload, context) -> ServerTabs.pick(context.player(), payload.id()));
        ServerPlayNetworking.registerGlobalReceiver(PrefsC2S.TYPE, (payload, context) -> ServerTabs.prefs(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(DoneC2S.TYPE, (payload, context) -> ServerTabs.done(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(ServerTabs::tick);
    }
}
