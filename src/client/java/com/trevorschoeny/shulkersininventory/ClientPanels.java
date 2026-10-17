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
import net.minecraft.network.chat.Component;

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
    private static final int CLOSE_SIZE = 11;

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
     * The close button: a raised button with a plain "x", in its own unstyled panel
     * centred on the item panel's top-right corner, so it sits over both edges and the
     * whole button takes clicks (Trev, 2026-10-16). It follows the item panel wherever
     * MenuKit places it, read each frame ({@code Panel.placedBounds}).
     */
    private static void closePanel(Openable kind, Panel itemPanel) {
        Panel close = Panel.builder(kind.panelId() + "_close")
                .add(Button.builder()
                        .label(Component.literal("x"))
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> ClientPlayNetworking.send(new CloseC2S(kind)))
                        .build())
                .style(PanelStyle.NONE)
                .position(PanelPosition.pixel(new CornerOf(itemPanel)))
                .build()
                .visibleWhen(itemPanel::isVisible);
        new ScreenPanelAdapter(close, 0).onMatching(ScreenMatcher.all());
    }

    /**
     * The item panel's top-right corner, minus half the button, so the button is
     * centred on it.
     *
     * <p>ponytail: re-entry guard. {@code placedBounds()} lays out the screen's panels,
     * this one included, whose position is this supplier, so it would recurse until the
     * stack overflows (crashed in game, 2026-10-16; reported to MenuKit). While a call
     * is under way, the nested one answers last frame's corner. Remove once MenuKit's
     * {@code placedBounds} doesn't re-enter layout.
     */
    private static final class CornerOf implements java.util.function.Supplier<ScreenOrigin> {
        private final Panel itemPanel;
        private boolean measuring = false;
        // Off screen until the item panel is first placed (hidden then anyway).
        private ScreenOrigin last = new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);

        CornerOf(Panel itemPanel) {
            this.itemPanel = itemPanel;
        }

        @Override
        public ScreenOrigin get() {
            if (measuring) return last;
            measuring = true;
            try {
                Reference box = itemPanel.placedBounds();
                if (box != null) {
                    last = new ScreenOrigin(box.leftPos() + box.imageWidth() - CLOSE_SIZE / 2,
                            box.topPos() - CLOSE_SIZE / 2);
                }
                return last;
            } finally {
                measuring = false;
            }
        }
    }
}
