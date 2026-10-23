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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A section's slots, growing with the group's rows (a bundle as it fills, a box with more
 * rows) until the panel would leave the screen, then scrolling inside the grid alone, as
 * Shulker Palette's box grid does. The tabs and the inventory stay put.
 *
 * <p>Kept in Open Anywhere until another mod needs it, then it moves to MenuKit or SlotKit
 * as a primitive.
 */
final class SlotScroll {

    private SlotScroll() {}

    /**
     * The most a grid may take on {@code screen}, in whole rows: the room MenuKit's fitted
     * list leaves for a section's content, never under {@link #MIN_ROWS} (also while the
     * list isn't laid out yet, when MenuKit says 0).
     */
    private static int room(Screen screen) {
        return Math.max(MIN_ROWS, ContainerRegion.contentRoom(screen) / ROW) * ROW;
    }

    private static final int ROW = 18;
    /** Never fewer rows than a vanilla container shows, however short the window. */
    private static final int MIN_ROWS = 3;

    /**
     * A scroll area as tall as its slots up to a cap, set each frame from the window
     * ({@link #frame}). The cap is the declared height {@code fitHeight} reads. A grid that
     * may narrow (a bundle) drops to eight a row while it scrolls, so eight slots and the
     * scrollbar fill nine slots' width and the panel never widens for it;
     * one that must stay nine wide gets the scrollbar's lane beside it while it scrolls.
     * What shows is read once a frame into {@link #state}: MenuKit's layout asks each slot
     * many times a frame, and asking SlotKit each time made a shown box cost three
     * quarters of the frame rate.
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

        /** Whether its slots overflow this frame, and when it was last drawn. */
        boolean scrolls;
        long drawn;

        @Override
        public void render(RenderContext ctx) {
            drawn = Util.getMillis();
            super.render(ctx);
        }

        /** This frame: how many slots are live, which layout shows them, the height cap and the width. */
        void frame(int room) {
            state[2] = 1;
            int live = 0;
            while (live < nine.size() && nine.get(live).isVisible()) live++;
            state[2] = 0;
            state[0] = live;
            scrolls = (live + 8) / 9 * ROW > room;
            state[1] = narrows && scrolls ? 1 : 0;
            int per = state[1] == 1 ? 8 : 9;
            // As tall as the live rows, never the group's whole size: the scroll area measures
            // its hidden slots too.
            this.height = Math.max(ROW, Math.min(room, (live + per - 1) / per * ROW));
            this.width = ContainerRegion.CONTENT_WIDTH + (scrolls && !narrows ? LANE : 0);
            this.authoredWidth = width;
        }
    }

    private static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;

    // Weak: MenuKit builds a grid each time an item opens and lets it go when it closes.
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
                .content(cells), id, nine, narrows, state);
        GRIDS.add(scroll);
        // Sized now, not at the next frame: MenuKit makes an opened item's grid mid-frame
        // and lays the list out from it at once, so an unsized grid drew the panel 8 px tall
        // for a frame (a flash on opening).
        var screen = Minecraft.getInstance().gui.screen();
        if (screen != null) scroll.frame(room(screen));
        return scroll;
    }

    /** Each frame, before the screen lays out: every grid's rows, height cap and width. */
    static void register() {
        // Ahead of MenuKit's own init phase, so this frame listener runs before MenuKit lays
        // the list out from the grids' heights: after it, the list worked from last frame's
        // heights and the panel jumped a few frames after a box opened.
        Identifier early = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "slot_scroll");
        ScreenEvents.AFTER_INIT.addPhaseOrdering(early, Identifier.fromNamespaceAndPath("menukit", "container_region_bounds"));
        ScreenEvents.AFTER_INIT.register(early, (client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> {
                int room = room(s);
                for (Grid grid : GRIDS) grid.frame(room);
            });
        });
    }
}
