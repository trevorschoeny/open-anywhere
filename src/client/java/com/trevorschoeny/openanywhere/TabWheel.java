package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The wheel moves between tabs, one tab a notch, up to the left and down to the right,
 * anywhere on the screen, along where the tabs' items sit ({@link Tabs#walk}): down from a
 * container into what's inside it, and on to the next. It's the tabs' first, before the
 * screen, its lists and every panel on it, the tab row's own included ({@code TabWheelMixin}).
 * The one exception: over a slot with Mouse Tweaks' wheel tweak on, the wheel moves items.
 */
public final class TabWheel {

    private TabWheel() {}

    /** One turn of the wheel, {@code amount} up or down: the next or previous tab, if it's the tabs'. Whether it was used. */
    public static boolean turn(double amount) {
        if (!takes(amount)) return false;
        List<TabId> walk = Tabs.walk();
        int i = Math.max(0, walk.indexOf(Tabs.selected()));
        int next = amount < 0 ? i + 1 : i - 1;
        if (next >= 0 && next < walk.size()) Tabs.pick(walk.get(next));
        return true;
    }

    /** Whether a turn is the tabs': the wheel's on, the row shows with a tab to go to, and Mouse Tweaks isn't moving items. */
    private static boolean takes(double amount) {
        if (amount == 0 || !Settings.wheel() || Tabs.walk().size() < 2) return false;
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;
        return !(((ScreenAccessor) screen).openanywhere$hoveredSlot() != null && MOUSE_TWEAKS_WHEEL);
    }

    /**
     * Whether Mouse Tweaks is loaded with its wheel tweak on, read once at start-up from its
     * config ({@code yalter.mousetweaks.Main.config.wheelTweak}).
     */
    // ponytail: read once; a change in Mouse Tweaks' own settings takes a restart to reach here.
    private static final boolean MOUSE_TWEAKS_WHEEL = mouseTweaksWheel();

    private static boolean mouseTweaksWheel() {
        if (!FabricLoader.getInstance().isModLoaded("mousetweaks")) return false;
        try {
            Object config = Class.forName("yalter.mousetweaks.Main").getField("config").get(null);
            return config.getClass().getField("wheelTweak").getBoolean(config);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Loaded but unreadable: its default, on.
            return true;
        }
    }
}
