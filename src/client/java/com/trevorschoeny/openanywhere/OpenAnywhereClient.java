package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenFrames;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
 * or a click with the optional Open key held), the settings, the tab row ({@link Tabs},
 * {@link ClientPanels}), and what the server sends of it ({@link TabsS2C}). Escape is
 * vanilla's: it closes the screen, which closes everything behind it.
 */
public final class OpenAnywhereClient implements ClientModInitializer {

    static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "keys"));

    /**
     * An optional key: held while clicking an item, it opens it in one click. Unbound by
     * default; double-click is the way in, and this is for players who
     * want a key instead, set in Controls or in the settings.
     */
    public static final KeyMapping OPEN = new KeyMapping(
            "key.openanywhere.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    /**
     * Opens the settings: Inventory Plus's menu on the Open Anywhere tab with IP installed,
     * Open Anywhere's own screen without it. Unbound by default, so Mod Menu stays optional
     * and a player without it still has a way in.
     */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.openanywhere.open_settings", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    // Whether the last tick's screen was one the row stands on.
    private static boolean wasOnTabs;

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN);
        KeyMappingHelper.registerKeyMapping(OPEN_SETTINGS);
        // In the world the settings key is polled on the client tick, as vanilla's own keys
        // are; on a container screen it's matched on key press, and the settings return there.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_SETTINGS.consumeClick()) if (mc.gui.screen() == null) SettingsScreen.open(null);
        });
        Settings.load();
        // The settings live in Inventory Plus's menu, so they show only when IP is installed.
        if (FabricLoader.getInstance().isModLoaded("inventoryplus")) com.trevorschoeny.openanywhere.ip.SettingsTab.register();
        ClientPlayNetworking.registerGlobalReceiver(TabsS2C.TYPE, (payload, context) -> Tabs.receive(payload));
        Shown.client(Tabs.VIEW);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendPrefs());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Tabs.clear());
        // Back in the world from the screens (Escape, the inventory key): the row goes, and
        // the server closes what was opened by hand. A switch between tabs never passes
        // through no screen: the screen in front stays up until the next one's contents arrive.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean onTabs = Tabs.isTabScreen(mc.gui.screen());
            if (wasOnTabs && mc.gui.screen() == null) {
                Tabs.clear();
                if (ClientPlayNetworking.canSend(DoneC2S.TYPE)) ClientPlayNetworking.send(DoneC2S.INSTANCE);
            }
            wasOnTabs = onTabs;
        });
        ClientPanels.register();
        ClientMarks.register();
        // The host is vanilla's chest menu, so it gets vanilla's chest screen.
        MenuScreens.register(ContainerHost.TYPE, ContainerScreen::new);
        // Our tab row sits on top of a vanilla chest frame, which is what makes it too
        // tall on a short window. Scroll it there instead of letting it overflow.
        ScreenFrames.scrollsWhenShort(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((inner, event) -> {
                if (OPEN_SETTINGS.matches(event)) SettingsScreen.open(inner);
            });
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

    /** True when the item in {@code slot} is open: it has a tab, or, with every box tabbed, it's in front. */
    static boolean isOpen(Player player, Slot slot) {
        return Tabs.looksOpen(slot);
    }

    /**
     * A left or right click with the Open key held. The key may be a Keybindery chord
     * (Ctrl+X, say), and Keybindery reads every key of it from the input state, under any
     * screen.
     */
    private static boolean isOpenClick(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        return KeybinderyAPI.getInstance().isHeld(OPEN);
    }

    /** The frame the item in front shows in, for {@code ShownItem.SECTION}. */
    public static com.trevlar.menukit.api.panel.@org.jspecify.annotations.Nullable Reference frameOf(net.minecraft.client.gui.screens.Screen screen) {
        return ClientPanels.frame(screen);
    }

    /** Tells the server the settings that shape the row: on joining, and on every change. */
    static void sendPrefs() {
        if (!ClientPlayNetworking.canSend(PrefsC2S.TYPE)) return;
        int kinds = 0;
        for (Openable kind : Openable.values()) if (Settings.opens(kind)) kinds |= 1 << kind.ordinal();
        ClientPlayNetworking.send(new PrefsC2S(Settings.tabs() != Settings.TabMode.HAND, kinds));
    }
}
