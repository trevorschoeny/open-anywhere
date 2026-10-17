package com.trevorschoeny.shulkersininventory;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
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
                .add(closeButton(kind))
                .style(PanelStyle.RAISED)
                .position(position)
                .build()
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> ClientSlots.isShown(group.id()));
        new ScreenPanelAdapter(panel, PADDING).onMatching(ScreenMatcher.all());
    }

    /**
     * A raised button with a plain "x", centred on the panel's top-right corner so it
     * sits over both edges. Positions are content-relative; the corner is a padding out.
     * ponytail: an element of the item's panel until MenuKit can place a panel on
     * another panel's corner (asked 2026-10-16); then its own NONE panel, as Trev asked.
     */
    private static Button closeButton(Openable kind) {
        return Button.builder()
                .label(Component.literal("x"))
                .size(CLOSE_SIZE, CLOSE_SIZE)
                .at(ROW_WIDTH + PADDING - CLOSE_SIZE / 2, -PADDING - CLOSE_SIZE / 2)
                .onClick(() -> ClientPlayNetworking.send(new CloseC2S(kind)))
                .build();
    }
}
