package com.trevorschoeny.shulkersininventory;

import com.trevorschoeny.shulkersininventory.mixin.HoveredSlotAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * Client init: the shift+right-click that asks to open an item, Escape to close it, and
 * the mirror of what the server opened ({@link SessionS2C}).
 */
public final class ShulkersInInventoryClient implements ClientModInitializer {

    /** GLFW's right mouse button. */
    private static final int RIGHT_BUTTON = 1;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Sessions.CLIENT_SHOWN.clear());

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // Not the creative inventory (design, Rule 4).
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)
                    || screen instanceof CreativeModeInventoryScreen) return;

            // The trigger is the real mouse, read before the screen sees the click, so a
            // simulated click (a sort mod, Mouse Tweaks) never opens anything.
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != RIGHT_BUTTON || !event.hasShiftDown()) return true;
                if (!ClientPlayNetworking.canSend(OpenC2S.TYPE)) return true; // server without the mod
                Slot hovered = ((HoveredSlotAccessor) containerScreen).shulkersininventory$hoveredSlot();
                if (hovered == null || Openable.of(hovered.getItem()) == null) return true;
                if (!containerScreen.getMenu().getCarried().isEmpty()) return true;
                ClientPlayNetworking.send(new OpenC2S(hovered.index));
                return false; // instead of vanilla's shift+right-click quick-move
            });

            // Escape closes the top panel first; with none open, it closes the screen.
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!event.isEscape() || Sessions.CLIENT_SHOWN.isEmpty()) return true;
                ClientPlayNetworking.send(new CloseC2S());
                return false;
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
            Sessions.CLIENT_SHOWN.remove(kind);
            return;
        }
        Slot slot = player.containerMenu.getSlot(payload.menuSlot());
        Sessions.put(player, new Session(kind, player.containerMenu, slot.container, slot.getContainerSlot(),
                payload.menuSlot(), slot.getItem()));
        Sessions.CLIENT_SHOWN.removeIf(open -> open.isEnderChest() == kind.isEnderChest());
        Sessions.CLIENT_SHOWN.add(kind);
    }
}
