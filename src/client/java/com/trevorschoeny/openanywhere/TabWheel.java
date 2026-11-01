package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.Reference;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * The wheel moves between tabs, one tab a notch, up to the left and down to the right,
 * anywhere on the screen, along where the tabs' items sit ({@link Tabs#walk}): down from a
 * container into what's inside it, and on to the next. Over the container, while it has a scrollbar
 * (the list's own, or a slot grid's), the wheel stays the container's. And only a turn nobody else used:
 * it runs after the screen and every other mod's scroll handler (Mouse Tweaks, a bundle's
 * own wheel), and before MenuKit's list scroll, which comes last of all.
 */
public final class TabWheel {

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
        if (!takes(screen, x, y, amount)) return false;
        List<TabId> walk = Tabs.walk();
        int i = Math.max(0, walk.indexOf(Tabs.selected()));
        int next = amount < 0 ? i + 1 : i - 1;
        if (next >= 0 && next < walk.size()) Tabs.pick(walk.get(next));
        return true;
    }

    /**
     * Whether a turn at {@code (x, y)} is the tabs': the wheel's on, there's a tab to go to, and
     * the mouse isn't over a list that scrolls. Over the container, the wheel is the
     * container's only while it has a scrollbar (the list's own, or a shown grid's); over a
     * vanilla screen's own list (the merchant's offers, the stonecutter's recipes, the loom's
     * patterns), only while there are more than it shows, anywhere on the frame, as vanilla scrolls it.
     */
    public static boolean takes(Screen screen, double x, double y, double amount) {
        if (amount == 0 || !Settings.wheel() || Tabs.walk().size() < 2) return false;
        if (ContainerRegion.scrolls(screen) && over(ContainerRegion.CONTAINERS.box(screen), x, y)) return false;
        if (screen instanceof AbstractContainerScreen<?> acs && ownListScrolls(acs)) {
            ScreenAccessor f = (ScreenAccessor) acs;
            return !over(new Reference(f.openanywhere$leftPos(), f.openanywhere$topPos(), f.openanywhere$imageWidth(), f.openanywhere$imageHeight()), x, y);
        }
        return true;
    }

    /** Whether the screen's own list has more than it shows, as each screen works it out. */
    private static boolean ownListScrolls(AbstractContainerScreen<?> screen) {
        return switch (screen.getMenu()) {
            case MerchantMenu m -> m.getOffers().size() > 7;
            case StonecutterMenu m -> m.getNumberOfVisibleRecipes() > 12;
            case LoomMenu m -> m.getSelectablePatterns().size() > 16;
            default -> false;
        };
    }

    private static boolean over(Reference box, double x, double y) {
        return box != null && x >= box.leftPos() && x < box.leftPos() + box.imageWidth()
                && y >= box.topPos() && y < box.topPos() + box.imageHeight();
    }
}
