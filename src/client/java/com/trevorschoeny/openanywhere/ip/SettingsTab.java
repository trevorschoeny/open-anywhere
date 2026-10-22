package com.trevorschoeny.openanywhere.ip;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.keybindery.chord.ChordButton;
import com.trevorschoeny.openanywhere.OpenAnywhere;
import com.trevorschoeny.openanywhere.OpenAnywhereClient;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Settings;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The Open Anywhere tab in Inventory Plus's settings menu, loaded only when IP is, laid out
 * as the Inventory Suite's other tabs (Simple Elytra Slot's): title, description, Reset to
 * Defaults, a line, then the settings. No On/Off of its own: the three kinds are the
 * switches (Trev, 2026-10-22). The Open key sits under the double-click it stands in for,
 * on Keybindery's chord button, which IP always brings.
 */
public final class SettingsTab {

    private SettingsTab() {}

    /** A divider's color on the inventory look, as on IP's own tabs. */
    private static final int LINE_COLOR = 0xFF8B8B8B;

    /** Call once from client init, before the menu can be opened. */
    public static void register() {
        Tabs.addTo(InventoryPlusApi.SETTINGS_MENU,
                Tabs.tab(OpenAnywhere.MOD_ID).label(Component.translatable("openanywhere.settings")).body(SettingsTab::body));
        // General's Reset everything resets this tab too.
        InventoryPlusApi.registerSettingsReset(Settings::reset);
    }

    private static List<PanelElement> body() {
        return List.of(
                TextLabel.builder().at(0, 0)
                        .text(Component.translatable("openanywhere.settings").withStyle(ChatFormatting.BOLD))
                        .role(TextRole.HEADING).build(),
                TextLabel.builder().at(0, 14)
                        .text(Component.translatable("openanywhere.settings.description"))
                        .role(TextRole.BODY).build(),
                Button.builder().at(0, 30).label(Component.literal("Reset to Defaults")).size(0, 16)
                        .onClick(() -> InventoryPlusApi.confirmInSettings("Reset Open Anywhere to defaults?",
                                "Every setting on this tab goes back to how a fresh install has it.", Settings::reset))
                        .build(),
                Divider.horizontal().at(0, 50).color(LINE_COLOR).build(),
                kind(56, Openable.SHULKER, "openanywhere.settings.shulker_boxes"),
                kind(72, Openable.BUNDLE, "openanywhere.settings.bundles"),
                kind(88, Openable.ENDER_CHEST, "openanywhere.settings.ender_chest"),
                checkbox(108, "openanywhere.settings.double_click", Settings::doubleClick, Settings::setDoubleClick),
                ChordButton.builder(OpenAnywhereClient.OPEN).at(0, 124)
                        .label(Component.translatable("openanywhere.settings.open_key")).build(),
                checkbox(148, "openanywhere.settings.wheel", Settings::wheel, Settings::setWheel));
    }

    private static PanelElement kind(int y, Openable kind, String label) {
        return checkbox(y, label, () -> Settings.opens(kind), on -> Settings.setOpens(kind, on));
    }

    private static PanelElement checkbox(int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Checkbox.builder().at(0, y).label(Component.translatable(label)).state(get, set).build();
    }
}
