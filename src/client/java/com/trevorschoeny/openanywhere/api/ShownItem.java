package com.trevorschoeny.openanywhere.api;

import com.trevlar.menukit.api.panel.Anchor;
import com.trevorschoeny.openanywhere.OpenAnywhere;
import com.trevorschoeny.openanywhere.OpenAnywhereClient;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The opened item on screen now, for other mods' panels: the item whose screen is in front
 * (a shulker box on vanilla's shulker box screen, the ender chest on vanilla's ender chest
 * screen, a bundle on Open Anywhere's), as the server last showed it.
 *
 * <p>Read {@link #get()} each frame: it changes when the player picks another tab and is
 * null while the inventory, the world container, or any other screen is in front.
 *
 * <pre>{@code
 * Panel.builder("mymod:palette")
 *         .add(...)
 *         .in(ModeScope.BOTH, Placement.around(ShownItem.SECTION, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
 *         .build()
 *         .visibleWhen(() -> ShownItem.get() != null && isPalette(ShownItem.get().stack()));
 * }</pre>
 */
public final class ShownItem {

    private ShownItem() {}

    /** The frame the opened item shows in. Absent while no opened item is in front, so a panel placed on it hides with it. */
    public static final Anchor SECTION = Anchor.of(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "shown_item"),
            screen -> get() == null ? null : OpenAnywhereClient.frameOf(screen));

    /** The opened item whose screen is in front, or null. */
    public static OpenItems.@Nullable OpenItem get() {
        return OpenItems.shown(Minecraft.getInstance().player);
    }

    /** Declares {@link #SECTION} while MenuKit takes declarations. */
    public static void declare() {}
}
