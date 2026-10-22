package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotElement;

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
     * A scroll area as tall as its rows up to a cap, and a scrollbar wider only while it
     * scrolls, both set each frame from the window ({@link #frame}). The cap is the declared
     * height {@code fitHeight} reads; the width, nine slots, plus the scrollbar's lane while
     * the rows overflow. Each row's shown state is read once a frame into {@link #shown}:
     * MenuKit's layout asks a row many times a frame, and asking SlotKit each time is what
     * made a shown box cost three quarters of the frame rate.
     */
    private static final class Grid extends ScrollContainer {
        final List<SlotElement> firsts;
        final boolean[] shown;

        Grid(ScrollContainer.Builder b, List<SlotElement> firsts, boolean[] shown) {
            super(b);
            this.firsts = firsts;
            this.shown = shown;
        }

        /** This frame: which rows show, and the grid's height cap and width. */
        void frame(int room) {
            int rows = 0;
            for (int r = 0; r < firsts.size(); r++) {
                // The row's own visibleWhen reads shown[r]: set it on, then ask SlotKit.
                shown[r] = true;
                shown[r] = firsts.get(r).isVisible();
                if (shown[r]) rows++;
            }
            this.height = room;
            boolean scrolls = rows * ROW > room;
            this.width = ContainerRegion.CONTENT_WIDTH + (scrolls ? LANE : 0);
            this.authoredWidth = width;
        }
    }

    private static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;

    private static final List<Grid> GRIDS = new ArrayList<>();

    /**
     * The slot group {@code id}, up to {@code max} slots, as a section's content: rows of
     * nine, each shown while it has a live slot, scrolling past what fits. Single slot
     * elements, as Shulker Palette's grid has them: a {@code SlotGroupElement} inside a
     * {@code ScrollContainer} draws nothing.
     */
    static PanelElement of(SlotGroupId id, int max) {
        // Each slot placed at its own cell, straight in the scroll area: rows of nested
        // Flows made MenuKit measure every slot many times a frame.
        List<PanelElement> cells = new ArrayList<>();
        List<SlotElement> firsts = new ArrayList<>();
        boolean[] shown = new boolean[(max + 8) / 9];
        for (int i = 0; i < max; i++) {
            int row = i / 9;
            SlotElement cell = SlotElement.builder().slot(id, i).at((i % 9) * ROW, row * ROW)
                    .visibleWhen(() -> shown[row]).build();
            if (i % 9 == 0) firsts.add(cell);
            cells.add(cell);
        }
        Grid scroll = new Grid(ScrollContainer.builder()
                .size(ContainerRegion.CONTENT_WIDTH + LANE, MIN_ROWS * ROW)
                .fitHeight()
                .content(cells), firsts, shown);
        GRIDS.add(scroll);
        return scroll;
    }

    /** Each frame, before the screen lays out: every grid's rows, height cap and width. */
    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> {
                int room = Math.max(MIN_ROWS, (s.height - OVERHEAD) / ROW) * ROW;
                for (Grid grid : GRIDS) grid.frame(room);
            });
        });
    }
}
