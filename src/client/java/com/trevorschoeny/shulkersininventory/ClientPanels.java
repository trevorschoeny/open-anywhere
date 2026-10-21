package com.trevorschoeny.shulkersininventory;

import java.util.function.Supplier;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.slot.ClientSlots;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotFlowElement;
import com.trevorschoeny.shulkersininventory.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * What this mod adds to screens: the close button on vanilla's shulker box and ender
 * chest screens when they show an opened item, and the bundle's panel with its own
 * close button.
 */
final class ClientPanels {

    private ClientPanels() {}

    private static final int PADDING = 7;
    /** A row of 9 vanilla slots (18 pixels each): the width the bundle panel wraps at. */
    private static final int ROW_WIDTH = 9 * 18;
    /**
     * An 11 px raised button around a 7 px black "x" (MenuKit insets an icon 2 px). A
     * text label can't be this small: MenuKit pads a label to about 17 px each way.
     */
    private static final int CLOSE_SIZE = 11;
    private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "close");

    static void register() {
        // On vanilla's own screen, the close button centres on the screen frame's corner.
        closeButton("native", ClientPanels::nativeSession, () -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
            ScreenAccessor frame = (ScreenAccessor) screen;
            return new Reference(frame.shulkersininventory$leftPos(), frame.shulkersininventory$topPos(),
                    frame.shulkersininventory$imageWidth(), 0);
        });
        bundlePanel();
    }

    /** The shulker box or ender chest session whose menu is the screen's, if any. */
    private static @Nullable Session nativeSession() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
        for (Session s : Sessions.of(mc.player)) {
            if (s.kind() != Openable.BUNDLE && s.menu() == screen.getMenu()) return s;
        }
        return null;
    }

    /**
     * Stopgap until the bundle gets a menu of its own: its slots in a panel above the
     * inventory screen's frame, 9 to a row, shown while its slots are live.
     */
    private static void bundlePanel() {
        ParityGroup group = Groups.bundle();
        Panel panel = Panel.builder(Openable.BUNDLE.panelId())
                .add(SlotFlowElement.of(group))
                .style(PanelStyle.RAISED)
                // Both modes: it shows only with a bundle open, and an open bundle outlives a
                // /gamemode switch, so its place must too.
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10)))
                .build()
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> ClientSlots.isShown(group.id()));
        new ScreenPanelAdapter(panel, PADDING).onMatching(ScreenMatcher.all());
        var mc = Minecraft.getInstance();
        closeButton("bundle",
                () -> mc.player == null || !panel.isVisible() ? null : Sessions.get(mc.player, Openable.BUNDLE),
                panel::placedBounds);
    }

    /**
     * A raised button with a black "x", in its own unstyled panel centred on the top-right
     * corner of {@code box}, so it sits over both edges and the whole button takes clicks
     * (Trev, 2026-10-16). Shown while {@code session} has one; a click closes that one.
     */
    private static void closeButton(String name, Supplier<@Nullable Session> session, Supplier<@Nullable Reference> box) {
        Panel close = Panel.builder(ShulkersInInventory.MOD_ID + ":close_" + name)
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> {
                            Session open = session.get();
                            if (open != null) ClientPlayNetworking.send(new CloseC2S(open.kind()));
                        })
                        .build())
                .style(PanelStyle.NONE)
                // Both modes, like the session it closes (see the bundle panel).
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> {
                    Reference b = box.get();
                    // Off screen when there's nothing to close (hidden then anyway).
                    if (b == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(b.leftPos() + b.imageWidth() - CLOSE_SIZE / 2, b.topPos() - CLOSE_SIZE / 2);
                })))
                .build()
                .visibleWhen(() -> session.get() != null);
        new ScreenPanelAdapter(close, 0).onMatching(ScreenMatcher.all());
    }
}
