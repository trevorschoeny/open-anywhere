package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.dialog.ConfirmDialog;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.MKScreen;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;

import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Open Anywhere's own settings screen, for a player without Inventory Plus: the same page
 * as the tab in Inventory Plus's menu ({@link SettingsPage}), in the Default look, with
 * Done at the bottom. With Inventory Plus installed, {@link #open} goes to that tab
 * instead, so the settings are in one place.
 */
public final class SettingsScreen extends MKScreen {

    /** What the confirm dialog is asking about, or null while it's closed. */
    private record Asked(String title, String body, Runnable action) {}
    private static @Nullable Asked pending;

    private SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("openanywhere.settings"), ScreenKind.DEFAULT, List.of(page(), confirmPanel(), done(parent)));
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /**
     * The settings over {@code parent}: Inventory Plus's menu on the Open Anywhere tab when
     * Inventory Plus is installed, else this screen. Mod Menu's button and the Open Settings
     * key both come here.
     */
    public static Screen create(@Nullable Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("inventoryplus")) return com.trevorschoeny.openanywhere.ip.SettingsTab.screen(parent);
        pending = null;
        return new SettingsScreen(parent);
    }

    static void open(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(create(parent));
    }

    private static Panel page() {
        return Panel.builder(OpenAnywhere.MOD_ID + ":settings")
                .style(PanelStyle.NONE)
                .position(PanelPosition.main())
                .elements(SettingsPage.body((title, body, action) -> pending = new Asked(title, body, action), false))
                .build();
    }

    private static Panel confirmPanel() {
        return ConfirmDialog.builder()
                .id(OpenAnywhere.MOD_ID + ":settings_confirm")
                .title(() -> Component.literal(pending == null ? "" : pending.title()))
                .body(() -> Component.literal(pending == null ? "" : pending.body()))
                .onCancel(() -> pending = null)
                .onConfirm(() -> {
                    Asked asked = pending;
                    pending = null;
                    if (asked != null) asked.action().run();
                })
                .build()
                .visibleWhen(() -> pending != null);
    }

    // Done at the bottom center, the way every Options screen ends.
    private static Panel done(@Nullable Screen parent) {
        return Panel.builder(OpenAnywhere.MOD_ID + ":settings_done")
                .style(PanelStyle.NONE)
                .position(PanelPosition.region(OutsideRegion.BOTTOM_CENTER))
                .add(Button.builder().label(CommonComponents.GUI_DONE).size(200, 20)
                        .onClick(() -> Minecraft.getInstance().gui.setScreen(parent)).build())
                .build();
    }
}
