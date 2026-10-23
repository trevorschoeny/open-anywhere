package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevorschoeny.keybindery.chord.ChordButton;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The settings, as one page: the Open Anywhere tab in Inventory Plus's settings menu, and
 * Open Anywhere's own screen without Inventory Plus ({@link SettingsScreen}), so the two
 * never differ. Laid out as the Inventory Suite's other tabs: title, description, Reset to
 * Defaults, a line, then the settings. No On/Off of its own: the three kinds are the
 * switches. The Open key sits under the double-click it stands in for, on Keybindery's
 * chord button.
 */
public final class SettingsPage {

    private SettingsPage() {}

    /** Asks before an action: a title, one line of what it will do, then the action. */
    public interface Confirm {
        void ask(String title, String body, Runnable action);
    }

    /** A divider's color, as on Inventory Plus's own tabs. */
    private static final int LINE_COLOR = 0xFF8B8B8B;

    /**
     * The page's elements. {@code confirm} asks before Reset to Defaults, the way the menu
     * showing it asks. {@code heading}: the mod's name on top, for Inventory Plus's tab; Open
     * Anywhere's own screen has it as the screen's title already.
     */
    public static List<PanelElement> body(Confirm confirm, boolean heading) {
        List<PanelElement> page = new ArrayList<>();
        if (heading) page.add(TextLabel.builder().at(0, 0)
                .text(Component.translatable("openanywhere.settings").withStyle(ChatFormatting.BOLD))
                .role(TextRole.HEADING).build());
        // Everything below the heading moves up into its place when there's none.
        int top = heading ? 14 : 0;
        page.addAll(List.of(
                TextLabel.builder().at(0, top)
                        .text(Component.translatable("openanywhere.settings.description"))
                        .role(TextRole.BODY).build(),
                Button.builder().at(0, top + 16).label(Component.literal("Reset to Defaults")).size(0, 16)
                        .onClick(() -> confirm.ask("Reset Open Anywhere to defaults?",
                                "Every Open Anywhere setting goes back to how a fresh install has it.", Settings::reset))
                        .build(),
                Divider.horizontal().at(0, top + 36).color(LINE_COLOR).build(),
                kind(top + 42, Openable.SHULKER, "openanywhere.settings.shulker_boxes"),
                kind(top + 58, Openable.BUNDLE, "openanywhere.settings.bundles"),
                kind(top + 74, Openable.ENDER_CHEST, "openanywhere.settings.ender_chest"),
                checkbox(top + 94, "openanywhere.settings.double_click", Settings::doubleClick, Settings::setDoubleClick),
                ChordButton.builder(OpenAnywhereClient.OPEN).at(0, top + 110)
                        .label(Component.translatable("openanywhere.settings.open_key")).build(),
                checkbox(top + 134, "openanywhere.settings.every_box", Settings::everyBox, Settings::setEveryBox),
                checkbox(top + 150, "openanywhere.settings.wheel", Settings::wheel, Settings::setWheel),
                checkbox(top + 166, "openanywhere.settings.animation", Settings::animation, Settings::setAnimation)));
        return page;
    }

    private static PanelElement kind(int y, Openable kind, String label) {
        return checkbox(y, label, () -> Settings.opens(kind), on -> Settings.setOpens(kind, on));
    }

    private static PanelElement checkbox(int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Checkbox.builder().at(0, y).label(Component.translatable(label)).state(get, set).build();
    }
}
