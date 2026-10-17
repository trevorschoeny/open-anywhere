package com.trevorschoeny.shulkersininventory;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.shulkersininventory.mixin.HoveredSlotAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * Client init: the shift+right-click that opens an item (and closes it again), the
 * panels ({@link ClientPanels}), and the mirror of what the server opened ({@link SessionS2C}).
 * Escape is vanilla's: it closes the screen, which closes every panel with it.
 */
public final class ShulkersInInventoryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPanels.register();

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // Not the creative inventory (design, Rule 4).
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)
                    || screen instanceof CreativeModeInventoryScreen) return;

            // The trigger is the real mouse, read before the screen sees the click, so a
            // simulated click (a sort mod, Mouse Tweaks) never opens anything. On the
            // item that's already open it closes it (the server decides which).
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !event.hasShiftDown()) return true;
                if (!ClientPlayNetworking.canSend(OpenC2S.TYPE)) return true; // server without the mod
                Slot hovered = ((HoveredSlotAccessor) containerScreen).shulkersininventory$hoveredSlot();
                if (hovered == null || Openable.of(hovered.getItem()) == null) return true;
                if (!containerScreen.getMenu().getCarried().isEmpty()) return true;
                ClientPlayNetworking.send(new OpenC2S(hovered.index));
                return false; // instead of vanilla's shift+right-click quick-move
            });
        });
    }

    /**
     * Mirrors one server decision. The slot is resolved on the client's own copy of the
     * same menu, so the client's slot lock and gates name the same slot as the server's.
     */
    private static void mirror(Player player, SessionS2C payload) {
        Openable kind = payload.kind();
        if (payload.menuSlot() < 0 || payload.menuSlot() >= player.containerMenu.slots.size()) {
            Sessions.remove(player, kind);
            return;
        }
        Slot slot = player.containerMenu.getSlot(payload.menuSlot());
        Sessions.put(player, new Session(kind, player.containerMenu, slot.container, slot.getContainerSlot(),
                payload.menuSlot(), slot.getItem()));
    }
}
