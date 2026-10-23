package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client init: the clicks that open and close an item (a double-click, {@link DoubleClick},
 * or a click with the optional Open key held), the settings, the tabs and their sections
 * ({@link ClientPanels}), and the mirror of what the server opened ({@link SessionS2C}).
 * Escape is vanilla's: it closes the screen, which closes everything on it.
 */
public final class OpenAnywhereClient implements ClientModInitializer {

    static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "keys"));

    /**
     * An optional key: held while clicking an item, it opens it in one click. Unbound by
     * default; double-click is the way in (Trev, 2026-10-22), and this is for players who
     * want a key instead, set in Controls or on the settings tab.
     */
    public static final KeyMapping OPEN = new KeyMapping(
            "key.openanywhere.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private static final boolean KEYBINDERY = FabricLoader.getInstance().isModLoaded("keybindery");

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN);
        Settings.load();
        // The settings live in Inventory Plus's menu, so they show only when IP is installed.
        if (FabricLoader.getInstance().isModLoaded("inventoryplus")) com.trevorschoeny.openanywhere.ip.SettingsTab.register();
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPanels.register();
        // The host is vanilla's chest menu, so it gets vanilla's chest screen.
        MenuScreens.register(ContainerHost.TYPE, ContainerScreen::new);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            // The trigger is the real mouse, read before the screen sees the click, so a
            // simulated click (a sort mod, Mouse Tweaks) never opens anything. On an item
            // that's already open it closes it (the server decides which).
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!ClientPlayNetworking.canSend(OpenC2S.TYPE)) return true; // server without the mod
                Slot hovered = ((ScreenAccessor) containerScreen).openanywhere$hoveredSlot();
                if (hovered == null) return true;
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (isOpenClick(event)) {
                    if (!opensOrIsOpen(client.player, hovered) || !carried.isEmpty()) return true;
                    // On creative's screen, only its inventory tab, by the real menu's index.
                    if (s instanceof CreativeModeInventoryScreen c && !c.isInventoryOpen()) return true;
                    ClientPlayNetworking.send(new OpenC2S(s instanceof CreativeModeInventoryScreen ? realIndex(client.player, hovered) : hovered.index, false));
                    return false; // instead of the click
                }
                return !DoubleClick.click(s, event, hovered, carried, client.player);
            });
        });
    }

    /** Creative's slot {@code shown} on the player's real inventory menu: the slot holding the same place. -1 for none. */
    static int realIndex(Player player, Slot shown) {
        for (Slot real : player.inventoryMenu.slots) {
            if (real.container == shown.container && real.getContainerSlot() == shown.getContainerSlot()) return real.index;
        }
        return -1;
    }

    /**
     * An item a click may act on: one whose kind is on in {@link Settings}, or one already
     * open, so a kind turned off while open can still be closed.
     */
    static boolean opensOrIsOpen(Player player, Slot slot) {
        Openable kind = Openable.of(slot.getItem());
        return kind != null && (Settings.opens(kind) || isOpen(player, slot));
    }

    /** True when the item in {@code slot} is open: a session holds that slot of the open menu. */
    static boolean isOpen(Player player, Slot slot) {
        for (Session s : Sessions.of(player)) if (s.menu() == player.containerMenu && s.menuSlot() == slot.index) return true;
        return false;
    }

    /**
     * A left or right click with the Open key held. With Keybindery the key may be a chord,
     * and every key of it is read, under any screen. Without it, a modifier key is read from
     * the click itself, as vanilla reads Shift, so a click arriving with Alt set counts
     * whatever the keyboard says now; any other key is read from the keyboard.
     */
    private static boolean isOpenClick(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        if (KEYBINDERY) return com.trevorschoeny.openanywhere.ip.ChordKey.isHeld(OPEN);
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
            // Closed with its button or a double-click: back to the inventory screen.
            if (payload.toInventory()) Minecraft.getInstance().gui.setScreen(new InventoryScreen(player));
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        boolean onMenu = payload.menuSlot() >= 0 && payload.menuSlot() < menu.slots.size();
        Slot slot = onMenu ? menu.getSlot(payload.menuSlot()) : null;
        Session session = new Session(kind, payload.lane(), menu, slot == null ? null : slot.container,
                slot == null ? -1 : slot.getContainerSlot(), payload.menuSlot(),
                slot == null ? ItemStack.EMPTY : slot.getItem());
        Sessions.put(player, session);
        // What was just opened is what the player wants to see: its tab is picked, and its
        // slot isn't greyed until the tab shows (ClientMarks).
        Tabs.pick(Tabs.idOf(session));
        ClientMarks.awaitTab(session);
    }
}
