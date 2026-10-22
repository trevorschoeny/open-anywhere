package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevorschoeny.openanywhere.ClientPanels.Source;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Prototype, not the feature: two ways to show many opened containers, picked with the
 * buttons at the screen's top right so one Test Gallery scene can compare them (Trev,
 * 2026-10-21).
 *
 * <ol>
 *   <li><b>Tabs.</b> One container at a time: the chest, the ender chest, or one opened
 *       item. Flat tabs, no nesting, attached to the top edge of the container list the
 *       way the creative inventory's tabs are, the picked one open into it. The list stays
 *       attached to the inventory panel.</li>
 *   <li><b>Scroll with contents.</b> Every open container in the scrolling list, and a table
 *       of contents on its left. The entry for the section at the top of the view is
 *       large, the rest small, easing between sizes as the list scrolls. A click glides
 *       the list to that section in under half a second.</li>
 * </ol>
 *
 * In both, carrying an item onto a tab or an entry acts as a click.
 *
 * Whichever wins belongs in MenuKit's container list. What stands in for MenuKit here:
 * a section's place in the list is found by probing MenuKit's internal scroll (set it,
 * read the section's anchor, set it back, all inside one frame, so nothing is drawn
 * meanwhile), and the glide sets that internal scroll each frame. MenuKit would own both
 * as a public "reveal section".
 */
public final class LayoutPrototype {

    private LayoutPrototype() {}

    public enum Layout {
        TABS("Tabs"), CONTENTS("Scroll with contents");

        final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    public static Layout layout = Layout.TABS;

    /** One open container: its section, the icon and name it shows, and how deep it sits under another. */
    record Tab(String sectionId, ItemStack icon, Component name, int depth) {}

    // Tabs: the one section shown.
    private static @Nullable String picked;
    // Sections open last frame, to show a newly opened one at once.
    private static List<String> known = List.of();

    // Contents: the glide under way, toward the scroll that puts glideTo's section at the top.
    private static @Nullable String glideTo;
    private static double glideTarget, lastSet = Double.NaN;
    private static long glideStart, glideLast;
    /** A glide never takes longer than this: past it, the list lands (Trev: half a second at most). */
    private static final long GLIDE_CAP_MS = 450;

    // Carrying an item onto a tab: the tab it switched to, and where the mouse was, so
    // staying on that tab, or a tab moving under a still mouse, doesn't switch again.
    private static @Nullable String hoverId;
    private static double hoverX = Double.NaN, hoverY = Double.NaN;

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            // Before each frame, after MenuKit's own (registered in an earlier phase), so
            // the scroll set here is the one this frame draws.
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> {
                refreshPick();
                stepGlide();
            });
        });

        // Tabs, on the container list's top edge: 28 px above it, the picked tab 4 px into it.
        Panel tabs = Panel.builder(OpenAnywhere.MOD_ID + ":tabs")
                .add(new TabStrip())
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.pixel(() -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    Reference list = screen == null ? null : ContainerRegion.CONTAINERS.box(screen);
                    return list == null ? new ScreenOrigin(-100, -100) : new ScreenOrigin(list.leftPos(), list.topPos() - TabStrip.UP);
                })))
                .build()
                .visibleWhen(() -> layout == Layout.TABS && !all().isEmpty());
        new AnchorPanelAdapter(tabs);

        // The table of contents, left of the list.
        Panel contents = Panel.builder(OpenAnywhere.MOD_ID + ":contents")
                .add(new Contents())
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> layout == Layout.CONTENTS && !all().isEmpty());
        new AnchorPanelAdapter(contents);

        // The layout picker, top right of the screen: the layout's name over a button per layout.
        Panel picker = Panel.builder(OpenAnywhere.MOD_ID + ":layout_picker")
                .elements(Column.at(0, 0).spacing(2)
                        .add(TextLabel.builder().text(() -> Component.literal((layout.ordinal() + 1) + ". " + layout.label)).build())
                        .addRow(r -> {
                            r.spacing(2);
                            for (Layout l : Layout.values()) {
                                r.add(Button.builder()
                                        .label(Component.literal(String.valueOf(l.ordinal() + 1)))
                                        .onClick(() -> { layout = l; glideTo = null; })
                                        .build());
                            }
                        })
                        .build())
                .style(PanelStyle.DARK)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.screenAnchor(InsideRegion.TOP_RIGHT)))
                .build()
                .visibleWhen(ClientPanels::anyOpenHere);
        new ScreenPanelAdapter(picker, 4).onMatching(ScreenMatcher.all());
    }

    /** Whether section {@code sectionId} shows in the container list in the current layout. The sections ask every frame. */
    static boolean shows(String sectionId, Source source) {
        return layout == Layout.CONTENTS || sectionId.equals(picked);
    }

    // ── The open containers ────────────────────────────────────────────────

    /**
     * Every open container on the screen, in the list's order: the chest and what opened
     * from it, the ender chest and what opened from it, then what opened from the inventory.
     */
    static List<Tab> all() {
        var mc = Minecraft.getInstance();
        List<Tab> out = new ArrayList<>();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return out;
        List<Session> here = new ArrayList<>();
        for (Session s : Sessions.of(mc.player)) if (s.stacked() && s.menu() == screen.getMenu()) here.add(s);
        if (here.isEmpty()) return out;
        here.sort(Comparator.comparingInt(s -> ClientPanels.orderOf(s.kind(), s.lane(), sourceOr(s))));

        if (!BundleHost.is(screen.getMenu())) {
            out.add(new Tab(ClientPanels.CHEST_ID, screenIcon(), screen.getTitle(), 0));
            for (Session s : here) if (sourceOr(s) == Source.CONTAINER && s.kind() != Openable.ENDER_CHEST) out.add(tab(s, 1));
        }
        for (Session s : here) {
            if (s.kind() != Openable.ENDER_CHEST) continue;
            out.add(new Tab(Openable.ENDER_CHEST.panelId(0), s.stack(), Component.translatable("container.enderchest"), 0));
            for (Session c : here) if (c.kind() != Openable.ENDER_CHEST && sourceOr(c) == Source.ENDER_CHEST) out.add(tab(c, 1));
        }
        for (Session s : here) if (sourceOr(s) == Source.INVENTORY) out.add(tab(s, 0));
        return out;
    }

    private static Tab tab(Session s, int depth) {
        Component plain = s.kind() == Openable.SHULKER ? Component.translatable("container.shulkerBox")
                : Component.translatable("item.minecraft.bundle");
        return new Tab(ClientPanels.sectionId(s.kind(), s.lane(), sourceOr(s)), s.stack(),
                ClientPanels.title(s.kind(), s.lane(), plain), depth);
    }

    private static Source sourceOr(Session s) {
        if (s.kind() == Openable.ENDER_CHEST) return Source.ENDER_CHEST;
        Source src = ClientPanels.sourceOf(s);
        return src == null ? Source.INVENTORY : src;
    }

    /** The block the screen was opened on, as an icon; a chest when it can't tell. */
    private static ItemStack screenIcon() {
        var mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && mc.level != null) {
            var item = mc.level.getBlockState(hit.getBlockPos()).getBlock().asItem();
            if (item != Items.AIR) return new ItemStack(item);
        }
        return new ItemStack(Items.CHEST);
    }

    /** A click on {@code t}'s tab or entry: show it (tabs), or glide to it (contents). */
    private static void click(Tab t) {
        if (layout == Layout.TABS) picked = t.sectionId();
        else glideTo(t.sectionId());
    }

    /**
     * Carrying an item onto {@code t} acts as a click: once each time the mouse comes onto
     * it, and only once the mouse has moved since the last such click.
     */
    private static void hover(@Nullable Tab t, double mx, double my) {
        if (t == null) { hoverId = null; return; }
        var player = Minecraft.getInstance().player;
        boolean carrying = player != null && !player.containerMenu.getCarried().isEmpty();
        if (!carrying || t.sectionId().equals(hoverId) || mx == hoverX && my == hoverY) return;
        hoverId = t.sectionId();
        hoverX = mx;
        hoverY = my;
        click(t);
    }

    /** Tabs: a newly opened item shows at once; when the shown one closes, the first shows. */
    private static void refreshPick() {
        List<String> now = new ArrayList<>();
        for (Tab t : all()) now.add(t.sectionId());
        // The chest is never "newly opened": it appears with the first item opened on it.
        for (String id : now) if (!known.contains(id) && !id.equals(ClientPanels.CHEST_ID)) picked = id;
        known = now;
        if (picked != null && !now.contains(picked)) picked = null;
        if (picked == null && !now.isEmpty()) picked = now.getFirst();
    }

    /**
     * Tabs, another tab picked: whether {@code slot} is one of the open chest's own, which
     * the client then treats as inactive (HiddenChestSlotsMixin). Render thread only; the
     * integrated server's slots are left alone.
     */
    public static boolean hidesChestSlot(Slot slot) {
        if (layout != Layout.TABS || picked == null || picked.equals(ClientPanels.CHEST_ID)) return false;
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread() || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !ClientPanels.anyOpenHere()) return false;
        return !(slot.container instanceof Inventory) && CreatedSlot.of(slot) == null && screen.getMenu().slots.contains(slot);
    }

    // ── Contents: where each section is, and the glide ─────────────────────

    /**
     * The scroll that puts {@code sectionId}'s top where the list's first section sits
     * unscrolled, clamped to the list; {@code null} when it isn't in the list. Found by
     * probing: MenuKit's section anchor reads the scroll as it is when asked, so the scroll
     * is set to each probe, the anchor read, and the scroll put back, inside one frame.
     * ponytail: MenuKit's own "reveal section" would know the section's place outright.
     */
    private static @Nullable Double scrollFor(Screen screen, Geometry g, String sectionId) {
        double saved = ContainerRegions.scroll();
        try {
            // Where the first section sits unscrolled: every section lands there.
            ContainerRegions.setScroll(0);
            List<Tab> all = all();
            Reference first = all.isEmpty() ? null : ContainerRegion.anchor(all.getFirst().sectionId()).box(screen);
            int lead = first == null ? 0 : first.topPos() - g.viewTop();
            // Half a view a probe: the section's top is below the view's top in one of them.
            for (double p = 0; p <= g.content(); p += Math.max(1, g.viewHeight() / 2)) {
                ContainerRegions.setScroll(p);
                Reference box = ContainerRegion.anchor(sectionId).box(screen);
                // A section is cut at the view's top, so only a box below it shows its own top.
                if (box != null && box.topPos() > g.viewTop()) {
                    return Scroller.clamp(p + box.topPos() - g.viewTop() - lead, g.content(), g.viewHeight());
                }
            }
            return null;
        } finally {
            ContainerRegions.setScroll(saved);
        }
    }

    private static void glideTo(String sectionId) {
        Screen screen = Minecraft.getInstance().gui.screen();
        Geometry g = screen == null ? null : ContainerRegions.geometry(screen);
        Double target = g == null ? null : scrollFor(screen, g, sectionId);
        if (target == null) return;
        glideTo = sectionId;
        glideTarget = target;
        glideStart = glideLast = Util.getMillis();
        lastSet = Double.NaN;
    }

    /**
     * One frame of the glide, with MenuKit's own easing (the one its tab strips glide
     * with); it lands outright once {@link #GLIDE_CAP_MS} has passed. The wheel, or
     * anything else moving the list meanwhile, ends it where it is.
     */
    private static void stepGlide() {
        if (glideTo == null) return;
        double now = ContainerRegions.scroll();
        if (!Double.isNaN(lastSet) && Math.abs(now - lastSet) > 0.5) { glideTo = null; return; }
        long t = Util.getMillis();
        boolean land = t - glideStart >= GLIDE_CAP_MS || Math.abs(glideTarget - now) < 0.5;
        double next = land ? glideTarget : Scroller.glide(now, glideTarget, t - glideLast);
        glideLast = t;
        ContainerRegions.setScroll(next);
        lastSet = next;
        if (land) glideTo = null;
    }

    /**
     * The section the contents mark: the one being glided to, else the last whose top has
     * passed a third of the way down the view, else (scrolled to the very end) the last in
     * view, else the first in view.
     */
    static @Nullable String current() {
        if (glideTo != null) return glideTo;
        Screen screen = Minecraft.getInstance().gui.screen();
        Geometry g = screen == null ? null : ContainerRegions.geometry(screen);
        if (g == null) return null;
        String firstInView = null, passed = null, lastInView = null;
        int line = g.viewTop() + g.viewHeight() / 3;
        for (Tab t : all()) {
            Reference box = ContainerRegion.anchor(t.sectionId()).box(screen);
            if (box == null) continue;
            if (firstInView == null) firstInView = t.sectionId();
            lastInView = t.sectionId();
            if (box.topPos() <= line) passed = t.sectionId();
        }
        if (ContainerRegions.scroll() >= Scroller.maxScroll(g.content(), g.viewHeight()) - 1 && lastInView != null) return lastInView;
        return passed != null ? passed : firstInView;
    }

    // ── For the self-checks ────────────────────────────────────────────────

    /** Clicks the {@code index}th open container's tab or entry, as the mouse would. */
    public static void clickForChecks(int index) {
        click(all().get(index));
    }

    /** The open containers' section ids, in order. */
    public static List<String> idsForChecks() {
        return all().stream().map(Tab::sectionId).toList();
    }

    /** The section the contents mark. */
    public static @Nullable String currentForChecks() {
        return current();
    }

    // ── The elements ───────────────────────────────────────────────────────

    /** A tab or entry as drawn last frame, for clicks. */
    private record Hit(Tab tab, int x, int y, int w, int h) {
        boolean has(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static @Nullable Tab hitAt(List<Hit> hits, double mx, double my) {
        for (Hit h : hits) if (h.has(mx, my)) return h.tab();
        return null;
    }

    /**
     * The tabs: vanilla's creative inventory top tabs, each with its container's icon.
     * Unpicked tabs are cut where the list's frame starts, so they sit behind it; the
     * picked one runs {@link #INTO} px into the frame, open into the list, as the
     * creative inventory draws them.
     */
    static final class TabStrip extends AbstractPanelElement {

        static final int W = 26, H = 32, INTO = 4, UP = H - INTO, STEP = W + 1;
        private static final Identifier FIRST_ON = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_1");
        private static final Identifier FIRST_OFF = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1");
        private static final Identifier ON = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");
        private static final Identifier OFF = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");

        private final List<Hit> hits = new ArrayList<>();

        TabStrip() {
            this.width = 9 * STEP;
            this.authoredWidth = width;
        }

        @Override public int getHeight() { return H; }

        @Override public boolean isInteractive() { return true; }

        @Override public void render(RenderContext ctx) {
            var g = ctx.graphics();
            List<Tab> tabs = all();
            int ox = ctx.originX() + getChildX(), oy = ctx.originY() + getChildY();
            hits.clear();
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int x = ox + i * STEP;
                boolean on = t.sectionId().equals(picked);
                Identifier sprite = i == 0 ? (on ? FIRST_ON : FIRST_OFF) : (on ? ON : OFF);
                // Unpicked: the sprite's top 28 px only, so the list's frame shows below it.
                if (on) g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, oy, W, H);
                else g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, W, H, 0, 0, x, oy, W, UP);
                g.item(t.icon(), x + 5, oy + 9);
                hits.add(new Hit(t, x, oy, W, on ? H : UP));
            }
            Tab over = hitAt(hits, ctx.mouseX(), ctx.mouseY());
            if (over != null) MKTooltip.queue(g, over.name(), ctx.mouseX(), ctx.mouseY());
            hover(over, ctx.mouseX(), ctx.mouseY());
        }

        @Override public void renderOverlay(RenderContext ctx) {}

        @Override public boolean mouseClicked(InputContext in, int button) {
            Tab t = hitAt(hits, in.mouseX(), in.mouseY());
            if (t == null) return false;
            click(t);
            return true;
        }
    }

    /**
     * The table of contents: an entry per open container, its icon and name, children
     * indented. The current entry ({@link #current}) eases to full size and white, the
     * others to {@link #SMALL} and grey, so the list reads like a page's contents as it
     * scrolls.
     */
    static final class Contents extends AbstractPanelElement {

        static final int W = 120, ROW = 18, GAP = 2, INDENT = 8;
        static final double SMALL = 0.65;
        /** How fast a size eases: each 60 ms covers about 63% of the way. */
        static final double EASE_MS = 60;

        /** Each entry's size this frame, by section id. */
        private final Map<String, Double> sizes = new HashMap<>();
        private long last = -1;
        private final List<Hit> hits = new ArrayList<>();

        Contents() {
            this.width = W;
            this.authoredWidth = width;
        }

        @Override public int getHeight() {
            int h = 0;
            for (Tab t : all()) h += (int) Math.round(ROW * sizes.getOrDefault(t.sectionId(), SMALL)) + GAP;
            return Math.max(ROW, h);
        }

        @Override public boolean isInteractive() { return true; }

        @Override public void render(RenderContext ctx) {
            var g = ctx.graphics();
            var font = Minecraft.getInstance().font;
            long now = Util.getMillis();
            double k = last < 0 ? 1 : 1 - Math.exp(-(now - last) / EASE_MS);
            last = now;
            String current = current();
            List<Tab> all = all();

            hits.clear();
            int ox = ctx.originX() + getChildX(), y = ctx.originY() + getChildY();
            for (Tab t : all) {
                boolean on = t.sectionId().equals(current);
                double s = sizes.getOrDefault(t.sectionId(), SMALL);
                s += ((on ? 1.0 : SMALL) - s) * k;
                sizes.put(t.sectionId(), s);

                int x = ox + t.depth() * INDENT, h = (int) Math.round(ROW * s);
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale((float) s, (float) s);
                g.item(t.icon(), 0, 1);
                g.text(font, t.name().getString(), 19, 5, on ? 0xFFFFFFFF : 0xFFA8A8A8);
                g.pose().popMatrix();

                hits.add(new Hit(t, x, y, W - t.depth() * INDENT, h));
                y += h + GAP;
            }
            sizes.keySet().retainAll(all.stream().map(Tab::sectionId).toList());
            hover(hitAt(hits, ctx.mouseX(), ctx.mouseY()), ctx.mouseX(), ctx.mouseY());
        }

        @Override public void renderOverlay(RenderContext ctx) {}

        @Override public boolean mouseClicked(InputContext in, int button) {
            Tab t = hitAt(hits, in.mouseX(), in.mouseY());
            if (t == null) return false;
            click(t);
            return true;
        }
    }
}
