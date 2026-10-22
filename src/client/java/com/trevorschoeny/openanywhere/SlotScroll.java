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
     * A scroll area as tall as its slots up to a cap, set each frame from the window
     * ({@link #frame}). The cap is the declared height {@code fitHeight} reads. A grid that
     * may narrow (a bundle) drops to eight a row while it scrolls, so eight slots and the
     * scrollbar fill nine slots' width and the panel never widens for it (Trev, 2026-10-22);
     * one that must stay nine wide gets the scrollbar's lane beside it while it scrolls.
     * What shows is read once a frame into {@link #state}: MenuKit's layout asks each slot
     * many times a frame, and asking SlotKit each time made a shown box cost three
     * quarters of the frame rate.
     */
    private static final class Grid extends ScrollContainer {
        final List<SlotElement> nine;
        final boolean narrows;
        /** {@code [0]}: slots live; {@code [1]}: 1 while eight a row; {@code [2]}: 1 while counting. */
        final int[] state;

        Grid(ScrollContainer.Builder b, List<SlotElement> nine, boolean narrows, int[] state) {
            super(b);
            this.nine = nine;
            this.narrows = narrows;
            this.state = state;
        }

        /** This frame: how many slots are live, which layout shows them, the height cap and the width. */
        void frame(int room) {
            state[2] = 1;
            int live = 0;
            while (live < nine.size() && nine.get(live).isVisible()) live++;
            state[2] = 0;
            state[0] = live;
            boolean scrolls = (live + 8) / 9 * ROW > room;
            state[1] = narrows && scrolls ? 1 : 0;
            this.height = room;
            this.width = ContainerRegion.CONTENT_WIDTH + (scrolls && !narrows ? LANE : 0);
            this.authoredWidth = width;
        }
    }

    private static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;

    private static final List<Grid> GRIDS = new ArrayList<>();

    /**
     * The slot group {@code id}, up to {@code max} slots, as a section's content, scrolling
     * past what fits; {@code narrows}: eight a row while it scrolls (a bundle). Each slot is
     * placed at its own cell straight in the scroll area (nested rows made MenuKit measure
     * every slot many times a frame); a narrowing grid has a second set placed eight a row,
     * one set shown at a time. Single slot elements, as Shulker Palette's grid has them: a
     * {@code SlotGroupElement} inside a {@code ScrollContainer} draws nothing.
     */
    static PanelElement of(SlotGroupId id, int max, boolean narrows) {
        int[] state = {0, 0, 0};
        List<PanelElement> cells = new ArrayList<>();
        List<SlotElement> nine = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            int index = i;
            SlotElement cell = SlotElement.builder().slot(id, i).at((i % 9) * ROW, (i / 9) * ROW)
                    .visibleWhen(() -> state[2] == 1 || state[1] == 0 && index < state[0]).build();
            nine.add(cell);
            cells.add(cell);
        }
        if (narrows) {
            for (int i = 0; i < max; i++) {
                int index = i;
                cells.add(SlotElement.builder().slot(id, i).at((i % 8) * ROW, (i / 8) * ROW)
                        .visibleWhen(() -> state[2] == 0 && state[1] == 1 && index < state[0]).build());
            }
        }
        Grid scroll = new Grid(ScrollContainer.builder()
                .size(ContainerRegion.CONTENT_WIDTH + LANE, MIN_ROWS * ROW)
                .fitHeight()
                .content(cells), nine, narrows, state);
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
