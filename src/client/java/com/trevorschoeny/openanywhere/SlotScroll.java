package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotElement;
import com.trevlar.menukit.api.element.Flow;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A section's slots, growing with the group's rows (a bundle as it fills, a box with more
 * rows) until the panel would leave the screen, then scrolling inside the grid alone, as
 * Shulker Palette's box grid does (Trev, 2026-10-22). The tabs and the inventory stay put.
 *
 * <p>Kept in Open Anywhere until another mod needs it (Trev): then it graduates to MenuKit
 * or SlotKit as a primitive.
 */
final class SlotScroll {

    private SlotScroll() {}

    private static final int ROW = 18;
    /** Never fewer rows than a vanilla container shows, however short the window. */
    private static final int MIN_ROWS = 3;
    /**
     * The height a container panel in tabs mode needs besides its grid: the tabs, the
     * frame, the inventory part and MenuKit's margins. ponytail: measured (a double chest
     * fits a 240 px window exactly, 240 - 6 rows); MenuKit's layout could hand this out.
     */
    private static final int OVERHEAD = 240 - 6 * ROW;

    /**
     * A scroll area as tall as its rows up to a cap ({@code fitHeight}), the cap set each
     * frame from the window. The cap is the declared height MenuKit's fit reads; set from
     * outside it would be overwritten by the column's own layout ({@code fillHeight}).
     */
    private static final class Grid extends ScrollContainer {
        Grid(ScrollContainer.Builder b) {
            super(b);
        }

        void cap(int pixels) {
            this.height = pixels;
        }
    }

    private static final List<Grid> GRIDS = new ArrayList<>();

    /**
     * The slot group {@code id}, up to {@code max} slots, as a section's content: rows of
     * nine, each shown while it has a live slot, scrolling past what fits. Single slot
     * elements, as Shulker Palette's grid has them: a {@code SlotGroupElement} inside a
     * {@code ScrollContainer} draws nothing (filed with MenuKit, 2026-10-21).
     */
    static PanelElement of(SlotGroupId id, int max) {
        List<PanelElement> rows = new ArrayList<>();
        for (int r = 0; r * 9 < max; r++) {
            List<SlotElement> cells = new ArrayList<>();
            for (int c = 0; c < 9 && r * 9 + c < max; c++) cells.add(SlotElement.builder().slot(id, r * 9 + c).build());
            Flow.Builder row = Flow.builder().gap(0, 0);
            for (SlotElement cell : cells) row.add(cell);
            rows.add(row.visibleWhen(() -> cells.getFirst().isVisible()).build());
        }
        PanelElement grid = Flow.builder().vertical().gap(0, 0).addAll(rows).build();
        int lane = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
        Grid scroll = new Grid(ScrollContainer.builder()
                .size(ContainerRegion.CONTENT_WIDTH + lane, MIN_ROWS * ROW)
                .fitHeight()
                .content(List.of(grid)));
        GRIDS.add(scroll);
        return scroll;
    }

    /** Each frame, before the screen lays out: every grid as tall as its rows, up to the room there is. */
    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> {
                int room = Math.max(MIN_ROWS, (s.height - OVERHEAD) / ROW) * ROW;
                for (Grid grid : GRIDS) grid.cap(room);
            });
        });
    }
}
