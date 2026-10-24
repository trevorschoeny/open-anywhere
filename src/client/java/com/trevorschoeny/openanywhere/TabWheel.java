package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.Reference;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * The wheel moves between tabs, one tab a notch, up to the left and down to the right,
 * anywhere on the screen. Down runs through a group's sub-tabs and on to the next group's
 * header; up runs back to the header and into the group before, at its last sub-tab. Over the container, while it has a scrollbar
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
        if (amount == 0 || !Settings.wheel()) return false;
        // The row as groups: each header, then its sub-tabs.
        List<List<TabId>> groups = new java.util.ArrayList<>();
        for (Tabs.Group g : Tabs.Group.values()) {
            TabId header = Tabs.header(g);
            if (header == null) continue;
            List<TabId> group = new java.util.ArrayList<>();
            group.add(header);
            group.addAll(Tabs.children(g));
            groups.add(group);
        }
        if (groups.stream().mapToInt(List::size).sum() < 2) return false;
        // Over the container, the wheel is the container's only while it has a scrollbar:
        // the list's own, or a shown grid's.
        boolean scrollable = ContainerRegion.scrolls(screen);
        Reference list = ContainerRegion.CONTAINERS.box(screen);
        if (scrollable && list != null && x >= list.leftPos() && x < list.leftPos() + list.imageWidth()
                && y >= list.topPos() && y < list.topPos() + list.imageHeight()) return false;
        TabId at = Tabs.selected();
        int g = 0, i = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            int ci = groups.get(gi).indexOf(at);
            if (ci >= 0) { g = gi; i = ci; break; }
        }
        // Down goes along: through the group's sub-tabs, then to the next group's header.
        // Up goes back: to the header, then into the group before at its last sub-tab.
        TabId next = null;
        if (amount < 0) {
            if (i + 1 < groups.get(g).size()) next = groups.get(g).get(i + 1);
            else if (g + 1 < groups.size()) next = groups.get(g + 1).getFirst();
        } else {
            if (i > 0) next = groups.get(g).get(i - 1);
            else if (g > 0) next = groups.get(g - 1).getLast();
        }
        if (next != null) Tabs.pick(next);
        return true;
    }
}
