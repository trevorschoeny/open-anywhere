package com.trevorschoeny.openanywhere.api;

import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevorschoeny.openanywhere.OpenAnywhere;
import com.trevorschoeny.openanywhere.Session;
import com.trevorschoeny.openanywhere.Sessions;
import com.trevorschoeny.openanywhere.Tabs;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The opened item on screen now, for other mods' panels. A chest's screen (or the screen
 * items open on from the inventory) shows one container at a time, picked by tabs: the
 * screen's own, or one opened item.
 *
 * <p>Read {@link #get()} each frame: it changes when the player picks another tab and
 * goes null when the item closes or the screen's own container shows.
 *
 * <pre>{@code
 * Panel.builder("mymod:palette")
 *         .add(...)
 *         .in(ModeScope.BOTH, Placement.around(ShownItem.SECTION, PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)))
 *         .build()
 *         .visibleWhen(() -> ShownItem.get() != null && isPalette(ShownItem.get().stack()));
 * }</pre>
 */
public final class ShownItem {

    private ShownItem() {}

    /**
     * The shown opened item's section on screen: its title, its slots and its scrollbar.
     * Absent while no opened item shows, so a panel placed on it hides with it.
     */
    public static final Anchor SECTION = Anchor.of(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "shown_item"),
            // One section shows at a time and the list is fitted to it, so the list's box is the section's.
            screen -> get() == null ? null : ContainerRegion.CONTAINERS.box(screen));

    /** The opened item whose section shows now, or null. */
    public static OpenItems.@Nullable OpenItem get() {
        var player = Minecraft.getInstance().player;
        String shown = Tabs.shown();
        if (player == null || shown == null) return null;
        for (Session s : Sessions.of(player)) {
            if (shown.equals(Tabs.idOf(s)))
                return new OpenItems.OpenItem(s.kind(), s.lane(), s.menuSlot(), s.container(), s.containerSlot());
        }
        return null;
    }

    /** Declares {@link #SECTION} while MenuKit takes declarations. */
    public static void declare() {}
}
