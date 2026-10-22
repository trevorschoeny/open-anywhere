package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.TextLabel;
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

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
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
 * Prototype, not the feature: three ways to show many opened containers, switched with a
 * key (L) so one Test Gallery scene can compare them (Trev, 2026-10-21).
 *
 * <ol>
 *   <li><b>Tabs with scroll.</b> The container list as it is, with a column of tabs on its
 *       left: one per open container, in the column's order, items opened from a container
 *       indented under it. A tab is lit while its section is on screen, greyed while it's
 *       scrolled out; a click scrolls to it.</li>
 *   <li><b>Tabs, no scroll.</b> The same tabs choose what shows: one container and one item
 *       opened from it at a time, so a chest and its box sit together and nothing scrolls.</li>
 *   <li><b>Two panels.</b> The list keeps the chest, the ender chest and what opened from
 *       them; items opened from the inventory move to a second panel on its right, with its
 *       own tabs and its own scroll.</li>
 * </ol>
 *
 * Whichever wins belongs in MenuKit's container list, for every mod's sections. Two things
 * here stand in for what MenuKit would own: jumping to a section uses MenuKit's internal
 * scroll setter, stepping until the section shows (MenuKit has no public "scroll to
 * section"), and the second panel is an ordinary panel, not a second list.
 */
public final class LayoutPrototype {

    private LayoutPrototype() {}

    public enum Layout {
        SCROLL_TABS("Tabs with scroll"), TABS("Tabs, no scroll"), TWO_PANELS("Two panels");

        final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    public static Layout layout = Layout.SCROLL_TABS;

    /** Switches layouts while a container screen is open. L by default. */
    static final KeyMapping SWITCH = new KeyMapping(
            "key.openanywhere.layout", InputConstants.Type.KEYBOARD, InputConstants.KEY_L, OpenAnywhereClient.CATEGORY);

    private static final int TAB = 20, TAB_GAP = 2, INDENT = 6;
    private static final Identifier LIT = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier OUT = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier PICKED = Identifier.withDefaultNamespace("widget/button_highlighted");

    /** One tab: a section, the icon and name it shows, how deep it sits, and its parent's section. */
    record Tab(String sectionId, ItemStack icon, Component name, int depth, @Nullable String parent, boolean right) {}

    // "Tabs, no scroll": the container shown, and the item shown from it.
    private static @Nullable String pickedRoot, pickedChild;
    // Sections seen last frame, to pick a newly opened one.
    private static Set<String> known = Set.of();
    // "Tabs with scroll": the section being scrolled to, and the last scroll set.
    private static @Nullable String jumpTo;
    private static double lastScroll = Double.NaN;
    private static int jumpFrames;

    static void register() {
        KeyMappingHelper.registerKeyMapping(SWITCH);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
                if (!SWITCH.matches(key)) return;
                layout = Layout.values()[(layout.ordinal() + 1) % Layout.values().length];
                jumpTo = null;
            });
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> {
                refreshPicks(s);
                stepJump(s);
            });
        });

        // The tab column left of the container list.
        Panel left = Panel.builder(OpenAnywhere.MOD_ID + ":tabs_left")
                .add(new TabColumn(false))
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> !tabs(false).isEmpty());
        new AnchorPanelAdapter(left);

        rightPanel();

        // Which layout is on, top right of the screen, clear of the tabs and the inventory.
        Panel label = Panel.builder(OpenAnywhere.MOD_ID + ":layout_label")
                .add(TextLabel.builder().text(() -> Component.literal("Layout: " + layout.label + "  (L switches)")).build())
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
            case TABS -> sectionId.equals(pickedRoot) || sectionId.equals(pickedChild);
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

    /** The tabs in one column: the left one, or the second panel's. */
    /** For the self-checks: clicks tab {@code index} of a column, as the mouse would. */
    public static void clickForChecks(boolean right, int index) {
        click(tabs(right).get(index));
    }

    /** For the self-checks: the section ids of a column's tabs, in order. */
    public static List<String> tabIdsForChecks(boolean right) {
        return tabs(right).stream().map(Tab::sectionId).toList();
    }

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
        if (layout == Layout.TABS) return t.sectionId().equals(pickedRoot) || t.sectionId().equals(pickedChild);
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && ContainerRegion.anchor(t.sectionId()).box(screen) != null;
    }

    private static void click(Tab t) {
        switch (layout) {
            case TABS -> {
                if (t.depth() == 0) {
                    pickedRoot = t.sectionId();
                    pickedChild = firstChild(t.sectionId());
                } else {
                    pickedRoot = t.parent();
                    pickedChild = t.sectionId();
                }
            }
            default -> {
                if (!t.right()) {
                    jumpTo = t.sectionId();
                    jumpFrames = 0;
                    lastScroll = Double.NaN;
                }
            }
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
     * Scrolls the list a step toward {@code jumpTo} each frame until its section's top
     * sits at the list's top, or the list can't go further.
     */
    private static void stepJump(Screen screen) {
        if (jumpTo == null) return;
        if (++jumpFrames > 240) { jumpTo = null; return; }
        Reference list = ContainerRegion.CONTAINERS.box(screen);
        if (list == null) { jumpTo = null; return; }
        Reference box = ContainerRegion.anchor(jumpTo).box(screen);
        double scroll = ContainerRegions.scroll();
        if (scroll == lastScroll && box != null) { jumpTo = null; return; }   // clamped: as far as it goes
        lastScroll = scroll;
        if (box != null && box.topPos() > list.topPos() + 1) {
            ContainerRegions.setScroll(scroll + Math.min(16, box.topPos() - list.topPos()));
        } else if (box != null && box.topPos() <= list.topPos() + 1 && box.topPos() >= list.topPos() - 1 && !targetAbove(screen)) {
            jumpTo = null;
        } else {
            ContainerRegions.setScroll(scroll + (targetAbove(screen) ? -16 : 16));
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

    /** A column of tabs, each a raised square with the container's icon, children indented. */
    static final class TabColumn extends AbstractPanelElement {

        private final boolean right;

        TabColumn(boolean right) {
            this.right = right;
            this.width = TAB + INDENT;
            this.authoredWidth = width;
        }

        @Override public int getHeight() {
            return Math.max(TAB, tabs(right).size() * (TAB + TAB_GAP) - TAB_GAP);
        }

        @Override public boolean isInteractive() { return true; }

        @Override public void render(RenderContext ctx) {
            List<Tab> tabs = tabs(right);
            var g = ctx.graphics();
            Tab hovered = null;
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int x = ctx.originX() + getChildX() + (right ? 0 : t.depth() * INDENT);
                int y = ctx.originY() + getChildY() + i * (TAB + TAB_GAP);
                boolean picked = layout == Layout.TABS && (t.sectionId().equals(pickedChild) || t.sectionId().equals(pickedRoot));
                g.blitSprite(RenderPipelines.GUI_TEXTURED, picked ? PICKED : lit(t) ? LIT : OUT, x, y, TAB, TAB);
                g.item(t.icon(), x + 2, y + 2);
                // Scrolled out of view: clearly dimmed, so lit and out read apart at a glance.
                if (!picked && !lit(t)) g.fill(x, y, x + TAB, y + TAB, 0xA0000000);
                if (ctx.mouseX() >= x && ctx.mouseX() < x + TAB && ctx.mouseY() >= y && ctx.mouseY() < y + TAB) hovered = t;
            }
            if (hovered != null) MKTooltip.queue(g, hovered.name(), ctx.mouseX(), ctx.mouseY());
        }

        @Override public void renderOverlay(RenderContext ctx) {}

        @Override public boolean mouseClicked(InputContext in, int button) {
            List<Tab> tabs = tabs(right);
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int x = in.originX() + getChildX() + (right ? 0 : t.depth() * INDENT);
                int y = in.originY() + getChildY() + i * (TAB + TAB_GAP);
                if (in.mouseX() >= x && in.mouseX() < x + TAB && in.mouseY() >= y && in.mouseY() < y + TAB) {
                    click(t);
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
                        .add(new TabColumn(true))
                        .build())
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> layout == Layout.TWO_PANELS && !tabs(true).isEmpty());
        new AnchorPanelAdapter(right);
    }
}
