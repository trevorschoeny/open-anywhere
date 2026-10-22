package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.inject.RegionTabs;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * The wheel moves between tabs, one tab a notch, up to the left and down to the right,
 * anywhere on the screen (Trev, 2026-10-22). Over the container, while it has a scrollbar
 * (the list's own, or a slot grid's), the wheel stays the container's. And only a turn nobody else used:
 * it runs after the screen and every other mod's scroll handler (Mouse Tweaks, a bundle's
 * own wheel), and before MenuKit's list scroll, which comes last of all.
 */
final class TabWheel {

    private TabWheel() {}

    private static final Identifier PHASE = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "tab_wheel");
    /** MenuKit's phase for its own late scroll ({@code ScreenPanelRegistry.listenLast}). */
    private static final Identifier MENUKIT_LAST = Identifier.fromNamespaceAndPath("menukit", "after_everyone");

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            var after = ScreenMouseEvents.afterMouseScroll(screen);
            after.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
            after.addPhaseOrdering(PHASE, MENUKIT_LAST);
            after.register(PHASE, (s, x, y, hAmount, vAmount, consumed) -> consumed || turn(s, x, y, vAmount));
        });
    }

    /** One turn of the wheel at {@code (x, y)}: the next or previous tab, unless over the list. Whether it was used. */
    private static boolean turn(Screen screen, double x, double y, double amount) {
        if (amount == 0) return false;
        // ponytail: MenuKit's internal row (the shown tab, then every tab in order); a public
        // read of the tab row would replace it.
        List<String> row = RegionTabs.rowForChecks();
        if (row == null || row.size() < 3) return false;
        // Over the container, the wheel is the container's only while it has a scrollbar:
        // the list's own, or a shown grid's.
        var g = com.trevlar.menukit.inject.ContainerRegions.geometry(screen);
        boolean scrollable = g != null && g.scrollable() || SlotScroll.anyScrolling();
        Reference list = ContainerRegion.CONTAINERS.box(screen);
        if (scrollable && list != null && x >= list.leftPos() && x < list.leftPos() + list.imageWidth()
                && y >= list.topPos() && y < list.topPos() + list.imageHeight()) return false;
        List<String> tabs = row.subList(1, row.size());
        int at = tabs.indexOf(row.getFirst());
        int next = Math.max(0, Math.min(tabs.size() - 1, at + (amount < 0 ? 1 : -1)));
        if (next != at) ContainerRegion.select(tabs.get(next));
        return true;
    }
}
