package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.layout.Row;
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
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.slotkit.api.slot.SlotGroupElement;
import com.trevorschoeny.openanywhere.ClientPanels.Source;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Prototype, not the feature: five ways to show many opened containers, picked with
 * buttons at the screen's top right so one Test Gallery scene can compare them (Trev,
 * 2026-10-21).
 *
 * <ol>
 *   <li><b>Side tabs with scroll.</b> The container list as it is, with a column of tabs on
 *       its left: one per open container, in the column's order, items opened from a
 *       container indented under it. A tab is lit while its section is on screen, greyed
 *       while it's scrolled out; a click scrolls to it.</li>
 *   <li><b>Side tabs, no scroll.</b> The same tabs choose what shows: one container and one
 *       item opened from it at a time, so a chest and its box sit together.</li>
 *   <li><b>Two panels.</b> The list keeps the chest, the ender chest and what opened from
 *       them; items opened from the inventory move to a second panel on its right, with its
 *       own tabs.</li>
 *   <li><b>Top tabs, no scroll.</b> As 2, with the tabs in two rows above the list: the
 *       containers, then the items opened from the picked one.</li>
 *   <li><b>Bottom tabs, no scroll.</b> As 4, with the rows between the list and the
 *       inventory (a section of the list, so the list makes room for them).</li>
 * </ol>
 *
 * In every layout, carrying an item over a tab acts as a click, so an item can be carried
 * to a container that isn't showing.
 *
 * Whichever wins belongs in MenuKit's container list, for every mod's sections. Two things
 * here stand in for what MenuKit would own: jumping to a section uses MenuKit's internal
 * scroll setter and geometry (MenuKit has no public "scroll to section"), and the second
 * panel is an ordinary panel, not a second list.
 */
public final class LayoutPrototype {

    private LayoutPrototype() {}

    public enum Layout {
        SCROLL_TABS("Side tabs, scroll"), TABS("Side tabs, no scroll"), TWO_PANELS("Two panels"),
        TOP_TABS("Top tabs, no scroll"), BOTTOM_TABS("Bottom tabs, no scroll");

        final String label;

        Layout(String label) {
            this.label = label;
        }

        /** True where the tabs pick what shows (no scroll), rather than scroll to it. */
        boolean picks() {
            return this == TABS || this == TOP_TABS || this == BOTTOM_TABS;
        }

        /** True where the tabs sit in a column left of the list. */
        boolean side() {
            return this == SCROLL_TABS || this == TABS || this == TWO_PANELS;
        }
    }

    public static Layout layout = Layout.SCROLL_TABS;

    private static final int TAB = 20, TAB_GAP = 2, INDENT = 6;
    private static final Identifier LIT = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier OUT = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier PICKED = Identifier.withDefaultNamespace("widget/button_highlighted");
    /** The tab rows as a section of the list, at {@code order}, shown in {@code in}. No title: the rows are the whole section. */
    private static void tabRows(String name, int order, Layout in) {
        ContainerRegion.section(OpenAnywhere.MOD_ID + ":" + name, ModeScope.BOTH)
                .content(new TabStrip(Strip.ROWS))
                .on(ContainerScreen.class)
                .order(order)
                .visibleWhen(() -> layout == in && ClientPanels.anyOpenHere())
                .declare();
    }

    /** Top or bottom tabs: scrolls the list to the end the tabs are at (MenuKit clamps it next frame). */
    private static void revealTabs() {
        if (layout == Layout.TOP_TABS) ContainerRegions.setScroll(0);
        if (layout == Layout.BOTTOM_TABS) ContainerRegions.setScroll(Integer.MAX_VALUE);
    }

    // ponytail: measured (the list's first section's top sits 4 px below the view's top
    // unscrolled); MenuKit's reveal(sectionId) would know it.
    private static final int LEAD = 4;

    /** One tab: a section, the icon and name it shows, how deep it sits, and its parent's section. */
    record Tab(String sectionId, ItemStack icon, Component name, int depth, @Nullable String parent, boolean right) {}

    /** Where a strip draws one tab, relative to the strip's own corner. */
    private record At(Tab tab, int x, int y) {}

    // The no-scroll layouts: the container shown, and the item shown from it.
    private static @Nullable String pickedRoot, pickedChild;
    // Sections seen last frame, to pick a newly opened one.
    private static Set<String> known = Set.of();
    // "Side tabs with scroll": the section being scrolled to, which way, and the last scroll seen.
    private static @Nullable String jumpTo;
    private static int jumpDir, jumpFrames;
    private static double lastScroll = Double.NaN;
    // Carrying an item over a tab: where the mouse was when it last switched. The next
    // switch waits for the mouse to move, so a layout that moves the tabs under a still
    // mouse can't switch again and again by itself.
    private static double hoverMouseX = Double.NaN, hoverMouseY = Double.NaN;
    // ...and the tab it switched to, in which strip, so staying on that tab doesn't switch again.
    private static @Nullable String hoverId;
    private static @Nullable Strip hoverStrip;

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> {
                refreshPicks(s);
                stepJump(s);
            });
        });

        // The tab column left of the container list.
        Panel left = Panel.builder(OpenAnywhere.MOD_ID + ":tabs_left")
                .add(new TabStrip(Strip.LEFT))
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> layout.side() && !tabs(false).isEmpty());
        new AnchorPanelAdapter(left);

        // The tab rows above the list and between the list and the inventory: the list's
        // first and last sections, so the list makes room for them. ponytail: they scroll
        // with the list; a pick scrolls them back into view (revealTabs). MenuKit would own
        // a header or footer that stays put.
        tabRows("tabs_top", -10_000, Layout.TOP_TABS);
        tabRows("tabs_bottom", 10_000, Layout.BOTTOM_TABS);

        rightPanel();

        // The layout picker, top right of the screen: the layout's name over a button per layout.
        Column.Builder picker = Column.at(0, 0).spacing(2)
                .add(TextLabel.builder().text(() -> Component.literal((layout.ordinal() + 1) + ". " + layout.label)).build())
                .addRow(r -> {
                    r.spacing(2);
                    for (Layout l : Layout.values()) {
                        r.add(Button.builder()
                                .label(Component.literal(String.valueOf(l.ordinal() + 1)))
                                .onClick(() -> { layout = l; jumpTo = null; revealTabs(); })
                                .build());
                    }
                });
        Panel label = Panel.builder(OpenAnywhere.MOD_ID + ":layout_picker")
                .elements(picker.build())
                .style(PanelStyle.DARK)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.screenAnchor(InsideRegion.TOP_RIGHT)))
                .build()
                .visibleWhen(ClientPanels::anyOpenHere);
        new ScreenPanelAdapter(label, 4).onMatching(ScreenMatcher.all());
    }

    /**
     * Whether section {@code sectionId}, from {@code source}, shows in the container list
     * in the current layout. The sections ask this every frame.
     */
    static boolean shows(String sectionId, Source source) {
        return switch (layout) {
            case SCROLL_TABS -> true;
            case TWO_PANELS -> source != Source.INVENTORY;
            case TABS, TOP_TABS, BOTTOM_TABS -> sectionId.equals(pickedRoot) || sectionId.equals(pickedChild);
        };
    }

    // ── The tabs ───────────────────────────────────────────────────────────

    /**
     * The tabs for the open screen, in the column's order: the chest and what opened from
     * it, the ender chest and what opened from it, then what opened from the inventory
     * (on the right in "Two panels").
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
            out.add(new Tab(ClientPanels.CHEST_ID, screenIcon(), screen.getTitle(), 0, null, false));
            for (Session s : here) if (sourceOr(s) == Source.CONTAINER && s.kind() != Openable.ENDER_CHEST) out.add(tab(s, 1, ClientPanels.CHEST_ID, false));
        }
        for (Session s : here) {
            if (s.kind() != Openable.ENDER_CHEST) continue;
            String ender = Openable.ENDER_CHEST.panelId(0);
            out.add(new Tab(ender, s.stack(), Component.translatable("container.enderchest"), 0, null, false));
            for (Session c : here) if (c.kind() != Openable.ENDER_CHEST && sourceOr(c) == Source.ENDER_CHEST) out.add(tab(c, 1, ender, false));
        }
        for (Session s : here) if (sourceOr(s) == Source.INVENTORY) out.add(tab(s, 0, null, layout == Layout.TWO_PANELS));
        return out;
    }

    /** For the self-checks: clicks tab {@code index} of a column, as the mouse would. */
    public static void clickForChecks(boolean right, int index) {
        click(tabs(right).get(index));
    }

    /** For the self-checks: the section ids of a column's tabs, in order. */
    public static List<String> tabIdsForChecks(boolean right) {
        return tabs(right).stream().map(Tab::sectionId).toList();
    }

    /** The tabs in one column: the left one (every tab but the second panel's), or the second panel's. */
    static List<Tab> tabs(boolean right) {
        List<Tab> out = new ArrayList<>();
        for (Tab t : all()) if (t.right() == right) out.add(t);
        return out;
    }

    private static Tab tab(Session s, int depth, @Nullable String parent, boolean right) {
        Component plain = s.kind() == Openable.SHULKER ? Component.translatable("container.shulkerBox")
                : Component.translatable("item.minecraft.bundle");
        return new Tab(ClientPanels.sectionId(s.kind(), s.lane(), sourceOr(s)), s.stack(),
                ClientPanels.title(s.kind(), s.lane(), plain), depth, parent, right);
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

    /** True while the tab's section shows in the list's view, or (second panel) at all. */
    private static boolean lit(Tab t) {
        if (t.right()) return true;
        if (layout.picks()) return picked(t);
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && ContainerRegion.anchor(t.sectionId()).box(screen) != null;
    }

    private static boolean picked(Tab t) {
        return t.sectionId().equals(pickedRoot) || t.sectionId().equals(pickedChild);
    }

    private static void click(Tab t) {
        if (layout.picks()) {
            if (t.depth() == 0) {
                pickedRoot = t.sectionId();
                pickedChild = firstChild(t.sectionId());
            } else {
                pickedRoot = t.parent();
                pickedChild = t.sectionId();
            }
            revealTabs();
        } else if (!t.right()) {
            startJump(t.sectionId());
        }
    }

    private static @Nullable String firstChild(String root) {
        for (Tab t : all()) if (root.equals(t.parent())) return t.sectionId();
        return null;
    }

    /**
     * "Tabs, no scroll": keeps the picks pointing at open sections, and picks a newly
     * opened item (with its container) so it shows at once.
     */
    private static void refreshPicks(Screen screen) {
        List<Tab> all = all();
        Set<String> now = new HashSet<>();
        for (Tab t : all) now.add(t.sectionId());
        for (Tab t : all) {
            if (known.contains(t.sectionId())) continue;
            // New this frame: show it, under its container.
            if (t.depth() > 0) { pickedRoot = t.parent(); pickedChild = t.sectionId(); }
            else if (t.right() || t.parent() == null && !t.sectionId().equals(ClientPanels.CHEST_ID)
                    && !t.sectionId().equals(Openable.ENDER_CHEST.panelId(0))) { pickedRoot = t.sectionId(); pickedChild = null; }
        }
        known = now;
        if (pickedRoot != null && !now.contains(pickedRoot)) pickedRoot = null;
        if (pickedChild != null && !now.contains(pickedChild)) pickedChild = null;
        if (pickedRoot == null && !all.isEmpty()) { pickedRoot = all.getFirst().sectionId(); pickedChild = firstChild(pickedRoot); }
    }

    /**
     * Starts scrolling the list to {@code sectionId}: down when its section is below the
     * view's top, up when it is cut at the top or above the view. The way is fixed here,
     * so the scroll can never turn back on itself.
     */
    private static void startJump(String sectionId) {
        Screen screen = Minecraft.getInstance().gui.screen();
        var g = screen == null ? null : ContainerRegions.geometry(screen);
        if (g == null) return;
        jumpTo = sectionId;
        jumpFrames = 0;
        lastScroll = Double.NaN;
        Reference box = ContainerRegion.anchor(sectionId).box(screen);
        jumpDir = box != null ? (box.topPos() > g.viewTop() ? 1 : -1) : (targetAbove(screen) ? -1 : 1);
    }

    /**
     * Scrolls the list toward {@code jumpTo}, one frame at a time, until its section's top
     * sits at the view's top. While the section's top is in view, that is one exact
     * scroll; until then the list steps 16 px a frame in the jump's way. It stops when the
     * list can't go further (MenuKit clamps the scroll at either end).
     */
    private static void stepJump(Screen screen) {
        if (jumpTo == null) return;
        var g = ContainerRegions.geometry(screen);
        double scroll = ContainerRegions.scroll();
        if (g == null || ++jumpFrames > 240 || scroll == lastScroll) { jumpTo = null; return; }
        lastScroll = scroll;
        Reference box = ContainerRegion.anchor(jumpTo).box(screen);
        // A section is cut at the view's top, so a box whose top is below it shows its own top.
        // It lands LEAD below the view's top, where the list's first section sits unscrolled.
        if (box != null && box.topPos() > g.viewTop()) {
            ContainerRegions.setScroll(scroll + box.topPos() - g.viewTop() - LEAD);
            jumpTo = null;
        } else {
            ContainerRegions.setScroll(scroll + 16 * jumpDir);
        }
    }

    /** True when the target section comes before every section in view. */
    private static boolean targetAbove(Screen screen) {
        List<Tab> left = tabs(false);
        int target = -1, firstLit = -1;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).sectionId().equals(jumpTo)) target = i;
            if (firstLit < 0 && ContainerRegion.anchor(left.get(i).sectionId()).box(screen) != null
                    && !left.get(i).sectionId().equals(jumpTo)) firstLit = i;
        }
        return firstLit >= 0 && target < firstLit;
    }

    /** Which tabs a strip shows, and how it lays them out. */
    enum Strip {
        /** Left of the list: every tab but the second panel's, in a column, children indented. */
        LEFT,
        /** The second panel's: what opened from the inventory, in a column. */
        RIGHT,
        /** Two rows: the containers, then what opened from the picked one. */
        ROWS
    }

    /** A strip of tabs, each a raised square with the container's icon. */
    static final class TabStrip extends AbstractPanelElement {

        private final Strip strip;

        TabStrip(Strip strip) {
            this.strip = strip;
            this.width = strip == Strip.ROWS ? ContainerRegion.CONTENT_WIDTH : TAB + INDENT;
            this.authoredWidth = width;
        }

        /** Where each tab goes this frame. */
        private List<At> placed() {
            List<At> out = new ArrayList<>();
            int step = TAB + TAB_GAP;
            switch (strip) {
                case LEFT, RIGHT -> {
                    List<Tab> tabs = tabs(strip == Strip.RIGHT);
                    for (int i = 0; i < tabs.size(); i++) {
                        Tab t = tabs.get(i);
                        out.add(new At(t, strip == Strip.RIGHT ? 0 : t.depth() * INDENT, i * step));
                    }
                }
                case ROWS -> {
                    int roots = 0, children = 0;
                    for (Tab t : tabs(false)) {
                        if (t.depth() == 0) out.add(new At(t, roots++ * step, 0));
                        else if (t.parent() != null && t.parent().equals(pickedRoot)) out.add(new At(t, children++ * step, step));
                    }
                }
            }
            return out;
        }

        @Override public int getHeight() {
            int bottom = TAB;
            for (At at : placed()) bottom = Math.max(bottom, at.y() + TAB);
            return bottom;
        }

        @Override public boolean isInteractive() { return true; }

        private static boolean over(At at, int ox, int oy, double mx, double my) {
            int x = ox + at.x(), y = oy + at.y();
            return mx >= x && mx < x + TAB && my >= y && my < y + TAB;
        }

        @Override public void render(RenderContext ctx) {
            var g = ctx.graphics();
            int ox = ctx.originX() + getChildX(), oy = ctx.originY() + getChildY();
            Tab hovered = null;
            for (At at : placed()) {
                Tab t = at.tab();
                int x = ox + at.x(), y = oy + at.y();
                boolean picked = layout.picks() && picked(t);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, picked ? PICKED : lit(t) ? LIT : OUT, x, y, TAB, TAB);
                g.item(t.icon(), x + 2, y + 2);
                // Scrolled out of view: clearly dimmed, so lit and out read apart at a glance.
                if (!picked && !lit(t)) g.fill(x, y, x + TAB, y + TAB, 0xA0000000);
                if (over(at, ox, oy, ctx.mouseX(), ctx.mouseY())) hovered = t;
            }
            if (hovered == null) {
                if (hoverStrip == strip) hoverId = null;
                return;
            }
            MKTooltip.queue(g, hovered.name(), ctx.mouseX(), ctx.mouseY());
            // Carrying an item onto a tab acts as a click: once each time the mouse comes
            // onto a tab, and only once the mouse has moved since the last such click.
            var mc = Minecraft.getInstance();
            boolean carrying = mc.player != null && !mc.player.containerMenu.getCarried().isEmpty();
            boolean moved = ctx.mouseX() != hoverMouseX || ctx.mouseY() != hoverMouseY;
            if (carrying && moved && !hovered.sectionId().equals(hoverId)) {
                hoverId = hovered.sectionId();
                hoverStrip = strip;
                hoverMouseX = ctx.mouseX();
                hoverMouseY = ctx.mouseY();
                click(hovered);
            }
        }

        @Override public void renderOverlay(RenderContext ctx) {}

        @Override public boolean mouseClicked(InputContext in, int button) {
            int ox = in.originX() + getChildX(), oy = in.originY() + getChildY();
            for (At at : placed()) {
                if (over(at, ox, oy, in.mouseX(), in.mouseY())) {
                    click(at.tab());
                    return true;
                }
            }
            return false;
        }
    }

    // ── "Two panels": the second panel ─────────────────────────────────────

    /**
     * Items opened from the inventory, in a panel right of the container list: each one's
     * name and slots, with its own tab column. ponytail: no scroll of its own yet (a slot
     * group inside a ScrollContainer in an ordinary panel drew nothing); MenuKit would own
     * this as a second list. Only in "Two panels"; in
     * the other layouts these items are sections of the list.
     */
    private static void rightPanel() {
        List<com.trevlar.menukit.api.element.PanelElement> blocks = new ArrayList<>();
        for (Openable kind : new Openable[] {Openable.SHULKER, Openable.BUNDLE}) {
            for (int lane = 0; lane < kind.lanes; lane++) {
                int l = lane;
                java.util.function.BooleanSupplier here = () -> layout == Layout.TWO_PANELS
                        && ClientPanels.sourceOf(ClientPanels.clientSession(kind, l)) == Source.INVENTORY;
                Component plain = kind == Openable.SHULKER ? Component.translatable("container.shulkerBox")
                        : Component.translatable("item.minecraft.bundle");
                blocks.add(TextLabel.builder().text(() -> ClientPanels.title(kind, l, plain)).visibleWhen(here).build());
                blocks.add(SlotGroupElement.builder(Groups.group(kind, lane).id()).visibleWhen(here).build());
            }
        }
        Panel right = Panel.builder(OpenAnywhere.MOD_ID + ":inventory_items")
                .elements(Row.at(0, 0).spacing(4)
                        .addColumn(c -> { c.spacing(0); for (var b : blocks) c.add(b); })
                        .add(new TabStrip(Strip.RIGHT))
                        .build())
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> layout == Layout.TWO_PANELS && !tabs(true).isEmpty());
        new AnchorPanelAdapter(right);
    }
}
