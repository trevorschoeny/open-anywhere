package com.trevorschoeny.shulkersininventory;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.slot.ClientSlots;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotFlowElement;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

/**
 * The panels the opened items show in: one MenuKit panel per kind, holding SlotKit's
 * slots for that kind and a close button. Each shows exactly while its slots are live,
 * which the server decides.
 *
 * <p>Stopgap placement (Trev, 2026-10-16: the column when the Kits have it): the
 * shulker box and bundle sit above the screen's frame, the ender chest to its left, 9
 * slots to a row. Only the placement changes when the column lands.
 */
final class ClientPanels {

    private ClientPanels() {}

    /** Space between a panel's edge and its slots. */
    private static final int PADDING = 7;
    /** A row of 9 vanilla slots (18 pixels each): the width the panels wrap at. */
    private static final int ROW_WIDTH = 9 * 18;
    /**
     * A 9 px raised button around a 5 px "x" (MenuKit insets an icon 2 px). A text label
     * can't be this small: MenuKit pads a label to about 17 px each way, and wraps it
     * taller when the button is narrower.
     */
    private static final int CLOSE_SIZE = 9;
    private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "close");

    static void register() {
        panel(Openable.SHULKER, PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10));
        panel(Openable.BUNDLE, PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10));
        panel(Openable.ENDER_CHEST, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP));
    }

    private static void panel(Openable kind, PanelPosition position) {
        ParityGroup group = Groups.of(kind);
        Panel panel = Panel.builder(kind.panelId())
                .add(SlotFlowElement.of(group))
                .style(PanelStyle.RAISED)
                .position(position)
                .build()
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> ClientSlots.isShown(group.id()));
        new ScreenPanelAdapter(panel, PADDING).onMatching(ScreenMatcher.all());
        closePanel(kind, panel);
    }

    /**
     * The close button: a raised button with a small "x", in its own unstyled panel
     * centred on the item panel's top-right corner, so it sits over both edges and the
     * whole button takes clicks (Trev, 2026-10-16). It follows the item panel wherever
     * MenuKit places it, read each frame ({@code Panel.placedBounds}).
     */
    private static void closePanel(Openable kind, Panel itemPanel) {
        Panel close = Panel.builder(kind.panelId() + "_close")
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> ClientPlayNetworking.send(new CloseC2S(kind)))
                        .build())
                .style(PanelStyle.NONE)
                .position(PanelPosition.pixel(() -> {
                    Reference box = itemPanel.placedBounds();
                    // Off screen until the item panel is placed (hidden then anyway).
                    if (box == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(box.leftPos() + box.imageWidth() - CLOSE_SIZE / 2,
                            box.topPos() - CLOSE_SIZE / 2);
                }))
                .build()
                .visibleWhen(itemPanel::isVisible);
        new ScreenPanelAdapter(close, 0).onMatching(ScreenMatcher.all());
    }
}
