package com.trevorschoeny.openanywhere.ip;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.openanywhere.OpenAnywhere;
import com.trevorschoeny.openanywhere.Settings;
import com.trevorschoeny.openanywhere.SettingsPage;

import com.trevlar.menukit.api.element.Tabs;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Open Anywhere tab in Inventory Plus's settings menu, loaded only when IP is: the
 * settings page ({@link SettingsPage}), with IP's own confirm for Reset to Defaults.
 */
public final class SettingsTab {

    private SettingsTab() {}

    /** Call once from client init, before the menu can be opened. */
    public static void register() {
        Tabs.addTo(InventoryPlusApi.SETTINGS_MENU, Tabs.tab(OpenAnywhere.MOD_ID)
                .label(Component.translatable("openanywhere.settings"))
                .body(() -> SettingsPage.body(InventoryPlusApi::confirmInSettings, true)));
        // General's Reset everything resets this tab too.
        InventoryPlusApi.registerSettingsReset(Settings::reset);
    }

    /** Inventory Plus's menu open on this tab, over {@code parent}. */
    public static Screen screen(@Nullable Screen parent) {
        return InventoryPlusApi.settingsScreen(parent, OpenAnywhere.MOD_ID);
    }
}
