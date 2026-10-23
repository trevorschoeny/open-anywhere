package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;

import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotElement;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A section's slots, growing with the group's rows (a bundle as it fills, a box with more
 * rows) until the panel would leave the screen, then scrolling inside the grid alone. The
 * tabs and the inventory stay put. MenuKit's {@code fitRoom} does the sizing: as tall as
 * the live rows while the window has room, shorter by whole rows when it hasn't.
 *
 * <p>Kept in Open Anywhere until another mod needs it, then it moves to MenuKit or SlotKit
 * as a primitive.
 */
final class SlotScroll {

    private SlotScroll() {}

    private static final int ROW = 18;
    /** Never fewer rows than a vanilla container shows, however short the window. */
    private static final int MIN_ROWS = 3;

    /**
     * A scroll area fitting the room ({@code fitRoom}). A grid that may narrow (a bundle)
     * drops to eight a row while it scrolls, so eight slots and the scrollbar fill nine
     * slots' width and the panel never widens for it; one that must stay nine wide gets the
     * scrollbar's lane beside it while it scrolls ({@code laneOutside}). What shows is read
     * once a frame into {@link #state}: MenuKit's layout asks each slot many times a frame,
     * and asking SlotKit each time made a shown box cost three quarters of the frame rate.
     */
    private static final class Grid extends ScrollContainer {
        final SlotGroupId id;
        final List<SlotElement> nine;
        final boolean narrows;
        /** {@code [0]}: slots live; {@code [1]}: 1 while eight a row; {@code [2]}: 1 while counting. */
        final int[] state;

        Grid(ScrollContainer.Builder b, SlotGroupId id, List<SlotElement> nine, boolean narrows, int[] state) {
            super(b);
            this.id = id;
            this.nine = nine;
            this.narrows = narrows;
            this.state = state;
        }

        /** Whether its slots overflowed at the last layout (MenuKit took rows back), and when it was last drawn. */
        boolean scrolls;
        long drawn;

        @Override
        public void render(RenderContext ctx) {
            drawn = Util.getMillis();
            super.render(ctx);
        }

        /**
         * MenuKit hands each layout's overflow down here, 0 first: rows given back mean it
         * scrolls. A narrowing grid switches to eight a row on the next frame (MenuKit decides
         * the overflow during layout, after the rows were placed). Eight a row is taller than
         * nine, so once narrowed it stays narrowed until eight a row fits too.
         */
        @Override
        public int yieldHeight(int overflow) {
            int given = super.yieldHeight(overflow);
            scrolls = given > 0;
            return given;
        }

        /** This frame: how many slots are live and which layout shows them. */
        void frame() {
            state[2] = 1;
            int live = 0;
            while (live < nine.size() && nine.get(live).isVisible()) live++;
            state[2] = 0;
            state[0] = live;
            state[1] = narrows && scrolls ? 1 : 0;
        }
    }

    // Weak, so a grid nothing shows any more goes.
    private static final java.util.Set<Grid> GRIDS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** True while a grid on screen (drawn in the last frames) has a scrollbar. */
    static boolean anyScrolling() {
        long now = Util.getMillis();
        for (Grid g : GRIDS) if (g.scrolls && now - g.drawn < 100) return true;
        return false;
    }

    /** When group {@code id}'s grid last drew, in {@code Util.getMillis()} time; 0 for never. */
    static long drawnAt(SlotGroupId id) {
        long at = 0;
        for (Grid g : GRIDS) if (g.id.equals(id)) at = Math.max(at, g.drawn);
        return at;
    }

    /**
     * The slot group {@code id}, up to {@code max} slots, as a section's content, scrolling
     * past what fits; {@code narrows}: eight a row while it scrolls (a bundle). Each slot is
     * placed at its own cell straight in the scroll area (nested rows made MenuKit measure
     * every slot many times a frame); a narrowing grid has a second set placed eight a row,
     * one set shown at a time. Single slot elements: a {@code SlotGroupElement} inside a
     * {@code ScrollContainer} draws nothing.
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
        ScrollContainer.Builder b = ScrollContainer.builder().size(ContainerRegion.CONTENT_WIDTH, MIN_ROWS * ROW);
        // A narrowing grid keeps its lane inside the width: eight a row leaves room for it.
        if (!narrows) b.laneOutside();
        Grid scroll = new Grid(b.fitRoom(ROW).content(cells), id, nine, narrows, state);
        GRIDS.add(scroll);
        return scroll;
    }

    /** Each frame, before the screen lays out: every grid's live slots and layout. */
    static void register() {
        // Ahead of MenuKit's own init phase, so this frame listener runs before MenuKit lays
        // the list out from the grids' rows: after it, the list worked from last frame's
        // rows and the panel jumped a few frames after a box opened.
        Identifier early = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "slot_scroll");
        ScreenEvents.AFTER_INIT.addPhaseOrdering(early, Identifier.fromNamespaceAndPath("menukit", "container_region_bounds"));
        ScreenEvents.AFTER_INIT.register(early, (client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> {
                for (Grid grid : GRIDS) grid.frame();
            });
        });
    }
}
