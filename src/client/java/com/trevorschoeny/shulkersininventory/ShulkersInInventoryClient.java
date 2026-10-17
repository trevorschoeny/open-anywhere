package com.trevorschoeny.shulkersininventory;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.slotkit.api.slot.ClientContainerPanel;
import com.trevorschoeny.shulkersininventory.mixin.HoveredSlotAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * Client init: the shift+right-click that opens an item (and closes it again), each
 * panel's close button, and the mirror of what the server opened ({@link SessionS2C}).
 * Escape is vanilla's: it closes the screen, which closes every panel with it.
 */
public final class ShulkersInInventoryClient implements ClientModInitializer {

    /** Vanilla's small cross, the one its own close buttons use. */
    private static final Identifier CLOSE = Identifier.withDefaultNamespace("widget/cross_button");
    private static final int CLOSE_SIZE = 11;

    @Override
    public void onInitializeClient() {
        Sessions.clientPlayer(() -> Minecraft.getInstance().player);
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Sessions.CLIENT_SHOWN.clear());

        // A small raised X centred on each panel's top-right corner, over both edges.
        // Element positions are content-relative; the corner is a padding out.
        for (Openable kind : Openable.values()) {
            ClientContainerPanel.of(kind.panelId()).chrome(() -> List.of(Button.builder()
                    .icon(CLOSE)
                    .size(CLOSE_SIZE, CLOSE_SIZE)
                    .at(Panels.ROW_WIDTH + Panels.PADDING - CLOSE_SIZE / 2, -Panels.PADDING - CLOSE_SIZE / 2)
                    .onClick(() -> ClientPlayNetworking.send(new CloseC2S(kind)))
                    .build()));
        }

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
