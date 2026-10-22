package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client init: the Option-click that opens an item (and closes it again), the sections
 * ({@link ClientPanels}), and the mirror of what the server opened ({@link SessionS2C}).
 * Escape is vanilla's: it closes the screen, which closes everything on it.
 */
public final class OpenAnywhereClient implements ClientModInitializer {

    static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "keys"));

    /**
     * Held while clicking an item to open it, Left Option (Alt) by default (Trev,
     * 2026-10-21): one key for every container operation, rebindable in Controls (bound
     * to Shift, it is the old shift-click).
     */
    public static final KeyMapping OPEN = new KeyMapping(
            "key.openanywhere.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN);
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPanels.register();
        LayoutPrototype.register();
        // The bundle's host is vanilla's chest menu, so it gets vanilla's chest screen.
        MenuScreens.register(BundleHost.TYPE, ContainerScreen::new);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            // The trigger is the real mouse, read before the screen sees the click, so a
            // simulated click (a sort mod, Mouse Tweaks) never opens anything. On an item
            // that's already open it closes it (the server decides which).
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!isOpenClick(event)) return true;
                // Survival only for now (design, Rule 4); read per click, so /gamemode with
                // a screen open takes effect at once.
                if (!ModeScope.SURVIVAL.includes(client.player)) return true;
                if (!ClientPlayNetworking.canSend(OpenC2S.TYPE)) return true; // server without the mod
                Slot hovered = ((ScreenAccessor) containerScreen).openanywhere$hoveredSlot();
                if (hovered == null || Openable.of(hovered.getItem()) == null) return true;
                if (!containerScreen.getMenu().getCarried().isEmpty()) return true;
                ClientPlayNetworking.send(new OpenC2S(hovered.index));
                return false; // instead of the click
            });
        });
    }

    /**
     * A left or right click with the open key held. A modifier key is read from the click
     * itself, as vanilla reads Shift, so a click arriving with Alt set counts whatever the
     * keyboard says now; any other key is read from the keyboard.
     */
    private static boolean isOpenClick(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN);
        if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return switch (key.getValue()) {
            case InputConstants.KEY_LALT, InputConstants.KEY_RALT -> event.hasAltDown();
            case InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> event.hasShiftDown();
            case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> event.hasControlDown();
            default -> InputConstants.isKeyDown(key.getValue());
        };
    }

    /**
     * Mirrors one server decision. The slot is resolved on the client's own copy of the
     * now-open menu, so the client's slot lock and grey name the same slot as the server's.
     */
    private static void mirror(Player player, SessionS2C payload) {
        Openable kind = payload.kind();
        if (!payload.open()) {
            Sessions.remove(player, kind, payload.lane());
            // Closed with its button or Option-click: back to the inventory screen.
            if (payload.toInventory()) Minecraft.getInstance().gui.setScreen(new InventoryScreen(player));
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        boolean onMenu = payload.menuSlot() >= 0 && payload.menuSlot() < menu.slots.size();
        Slot slot = onMenu ? menu.getSlot(payload.menuSlot()) : null;
        Sessions.put(player, new Session(kind, payload.lane(), menu, slot == null ? null : slot.container,
                slot == null ? -1 : slot.getContainerSlot(), payload.menuSlot(),
                slot == null ? ItemStack.EMPTY : slot.getItem()));
    }
}
