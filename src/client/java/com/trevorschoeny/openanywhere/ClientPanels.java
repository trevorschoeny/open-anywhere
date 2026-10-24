package com.trevorschoeny.openanywhere;


import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;

/**
 * What this mod adds to the screens: the tab row (three groups, the inventory, the world
 * container and the ender chest, through MenuKit's {@link ContainerTabs}), the bundle's
 * slots on its host, and the close button for the item in front.
 */
final class ClientPanels {

    private ClientPanels() {}

    /**
     * An 11 px raised button around a 7 px black "x" (MenuKit insets an icon 2 px). A
     * text label can't be this small: MenuKit pads a label to about 17 px each way.
     */
    private static final int CLOSE_SIZE = 11;
    private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "close");

    static final String INVENTORY_TAB = OpenAnywhere.MOD_ID + ":inventory";
    static final String WORLD_TAB = OpenAnywhere.MOD_ID + ":world";
    static final String ENDER_TAB = OpenAnywhere.MOD_ID + ":ender_chest";
    /** The open bundle's section on its host. */
    static final String BUNDLE_ID = OpenAnywhere.MOD_ID + ":bundle";

    /** The groups in order: the inventory, the world container, the ender chest. */
    private static final int INVENTORY_ORDER = 0, WORLD_ORDER = 100, ENDER_ORDER = 200;

    /** The screens the row stands on: the inventory, a chest (and the bundle's host), a shulker box. */
    @SuppressWarnings("unchecked")
    private static final Class<? extends AbstractContainerScreen<?>>[] SCREENS =
            new Class[] { InventoryScreen.class, ContainerScreen.class, ShulkerBoxScreen.class };

    // The world container's look, kept from its own screen so its tab shows it from any screen.
    // Null until a world screen is seen: items can't be made before the game has bound them.
    private static @Nullable ItemStack worldIcon;
    private static Component worldTitle = Component.translatable("container.chest");
    // Whether the world container is a double chest: its icon is the two halves together.
    private static boolean worldDouble;

    static void register() {
        SlotScroll.register();
        TabWheel.register();
        tabs();
        bundle();
        closeButton();
        com.trevorschoeny.openanywhere.api.ShownItem.declare();
    }

    /**
     * The row, as three groups: the inventory, the world container and the ender chest, each
     * a header with the items it holds as smaller sub-tabs that slide out from under it while
     * the group holds the tab in front (or always, with every group kept open). A header shows
     * its own screen when clicked. MenuKit draws the row and the slide; the icons are ours.
     */
    private static void tabs() {
        group(Tabs.Group.INVENTORY, INVENTORY_TAB, INVENTORY_ORDER, ClientPanels::drawHead,
                () -> Component.translatable("container.inventory"));
        group(Tabs.Group.WORLD, WORLD_TAB, WORLD_ORDER,
                ClientPanels::drawWorld,
                ClientPanels::worldTitle);
        group(Tabs.Group.ENDER_CHEST, ENDER_TAB, ENDER_ORDER,
                (g, x, y, w, h) -> {
                    TabId ender = Tabs.enderHeader();
                    if (ender != null) drawOpening(g, item(ender), Tabs.Group.ENDER_CHEST,
                            Tabs.groupOf(Tabs.selected()) == Tabs.Group.ENDER_CHEST, x, y, w);
                },
                () -> Component.translatable("container.enderchest"));
    }

    /** One group: its header, which shows its own screen, and its items as sub-tabs. */
    private static void group(Tabs.Group group, String id, int order, ContainerTabs.Icon headerIcon,
                              java.util.function.Supplier<Component> headerTooltip) {
        ContainerTabs.keyed(id, ModeScope.BOTH, () -> Tabs.children(group))
                .header(h -> h
                        .icon(headerIcon)
                        .tooltip(headerTooltip)
                        .visibleWhen(() -> Tabs.header(group) != null)
                        // Pinned at the row's left end while its group is open, when the row overflows.
                        .stickyWhen(() -> Tabs.expanded(group))
                        .selectedWhen(() -> {
                            TabId header = Tabs.header(group);
                            return header != null && header.equals(Tabs.selected());
                        })
                        .onClick(() -> {
                            TabId header = Tabs.header(group);
                            if (header != null) Tabs.pick(header);
                        }))
                .tab((tabId, tab) -> tab
                        .icon((g, x, y, w, h) -> drawOpening(g, item(tabId), tabId, Tabs.selected().equals(tabId), x, y, w))
                        .tooltip(() -> tooltip(tabId))
                        .selectedWhen(() -> Tabs.selected().equals(tabId))
                        // The tab in front pins beside its group's header.
                        .stickyWhen(() -> Tabs.selected().equals(tabId))
                        .onClick(() -> Tabs.pick(tabId)))
                .expandedWhen(() -> Tabs.expanded(group))
                .on(SCREENS)
                .order(order)
                .declare();
    }

    /**
     * {@code stack} in an icon box {@code size} wide: a box or chest opening while
     * {@code open} (the slot's own open animation, keyed by {@code key}), a bundle drawn open,
     * anything else as it is.
     */
    private static void drawOpening(GuiGraphicsExtractor g, ItemStack stack, Object key, boolean open, int x, int y, int size) {
        if (stack.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        if (Openable.of(stack) == Openable.BUNDLE) g.item(open ? ClientMarks.openBundle(stack) : stack, 0, 0);
        else ClientMarks.OPEN.draw(g, stack, 0, 0, ClientMarks.OPENING.toward(key, open && Settings.animation()));
        g.pose().popMatrix();
    }

    /**
     * The world header's icon: the container's block, opening while the world group holds the
     * tab in front. A double chest is drawn as in the world: one item model
     * ({@code assets/openanywhere/items/<chest>_double.json}) of vanilla's two half models with
     * their own half textures, the left half a block to the right half's +x, shrunk to fit.
     * One model, not two items: vanilla centres each item in its box, which would pull two
     * halves apart.
     */
    /**
     * The double chest's lid opening, kept centred as the single box's is ({@link ClientMarks#OPEN}):
     * the open lid would make it a third taller, all of it upward, so it shrinks to its closed
     * height and moves down to its closed centre. Measured on the drawn model, closed and open.
     */
    private static final ItemAnimation DOUBLE_OPEN = ItemAnimation.OPEN_LID
            .posed(t -> new ItemAnimation.Pose(1f - 0.23f * t, 0f, 1.15f * t));

    private static void drawWorld(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        ItemStack block = worldIcon();
        boolean open = Tabs.groupOf(Tabs.selected()) == Tabs.Group.WORLD;
        String path = BuiltInRegistries.ITEM.getKey(block.getItem()).getPath();
        if (!worldDouble || !HALVES.contains(path)) {
            drawOpening(g, block, Tabs.Group.WORLD, open, x, y, w);
            return;
        }
        float t = ClientMarks.OPENING.toward(Tabs.Group.WORLD, open && Settings.animation());
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(w / 16f, w / 16f);
        ItemStack pair = block.copy();
        pair.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, path + "_double"));
        DOUBLE_OPEN.draw(g, pair, 0, 0, t);
        g.pose().popMatrix();
    }

    /** The chests that make double chests: each has its double model in this mod's assets. */
    private static final java.util.Set<String> HALVES = java.util.Set.of("chest", "trapped_chest",
            "copper_chest", "exposed_copper_chest", "weathered_copper_chest", "oxidized_copper_chest",
            "waxed_copper_chest", "waxed_exposed_copper_chest", "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest");

    /** How long the head takes to turn, and how much larger it grows facing front, where only its face shows. */
    private static final Transition HEAD_TURN = Transition.of(250, Easing.EASE_OUT);
    private static final float HEAD_GROWTH = 0.35f;

    /**
     * The Inventory header's icon: the player's own head, turning to face front while the
     * inventory group holds the tab in front and back to vanilla's three-quarter view when
     * not. A head's GUI view is turned {@code [30, 45, 0]}; it faces the viewer at
     * {@code [0, 0, 0]}. Facing front only its face shows, so it grows to keep its size.
     */
    private static void drawHead(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        float t = HEAD_TURN.toward(Tabs.Group.INVENTORY, Tabs.groupOf(Tabs.selected()) == Tabs.Group.INVENTORY);
        float scale = w / 16f * (1f + HEAD_GROWTH * t);
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2f, y + h / 2f);
        g.pose().scale(scale, scale);
        g.pose().translate(-8, -8);
        ItemTurn.draw(-30f * t, -45f * t, () -> g.item(playerHead(), 0, 0));
        g.pose().popMatrix();
    }

    /** The inventory tab's icon: the player's own head. */
    private static ItemStack playerHead() {
        var player = Minecraft.getInstance().player;
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (player != null) head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        return head;
    }

    /**
     * The world container's tab icon: the block its screen was opened on
     * ({@link ContainerRegion#openedOn()}), a chest when there's none (a minecart, a large
     * chest). Read while its screen is up and kept for the other screens.
     */
    private static ItemStack worldIcon() {
        if (Tabs.shown().equals(TabId.WORLD) && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen) {
            ContainerRegion.Source source = ContainerRegion.openedOn();
            Block block = source == null || source.block() == null ? null : BuiltInRegistries.BLOCK.getValue(source.block());
            worldIcon = block == null ? new ItemStack(Items.CHEST) : new ItemStack(block);
            worldTitle = screen.getTitle();
            worldDouble = screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu chest && chest.getRowCount() == 6;
        }
        return worldIcon == null ? new ItemStack(Items.CHEST) : worldIcon;
    }

    private static Component worldTitle() {
        worldIcon();
        return worldTitle;
    }

    /** The item {@code id} stands for: its tab's icon. */
    private static ItemStack item(TabId id) {
        Tab tab = Tabs.tab(id);
        return tab == null ? ItemStack.EMPTY : tab.item();
    }

    /** An item tab's tooltip: its own name, as the inventory shows it ("Red Shulker Box", or the name the player gave it). */
    private static Component tooltip(TabId id) {
        Tab tab = Tabs.tab(id);
        return tab == null ? Component.empty() : tab.item().getHoverName();
    }

    /**
     * A bundle has no screen in vanilla: it opens on the host, vanilla's chest screen with no
     * rows, its slots a section of MenuKit's container list above the inventory, growing
     * with the bundle and scrolling past what fits ({@link SlotScroll}).
     */
    private static void bundle() {
        ContainerRegion.fitted(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));
        ContainerRegion.section(BUNDLE_ID, ModeScope.BOTH)
                .name(ClientPanels::bundleName)
                .title(ClientPanels::bundleName)
                .content(SlotScroll.of(Groups.bundleId(), Openable.BUNDLE.slots, true))
                .on(ContainerScreen.class)
                .visibleWhen(ClientPanels::onHost)
                .declare();
    }

    private static boolean onHost() {
        return Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen && ContainerHost.is(screen.getMenu());
    }

    private static Component bundleName() {
        Tab front = Tabs.shownItem();
        return front == null ? Component.translatable("item.minecraft.bundle") : front.item().getHoverName();
    }

    /**
     * The close button for the item in front: a raised button with a black "x", centred on
     * the frame's top-right corner, so it sits over the frame's edge, never over a slot or a
     * tab. Only for items opened by hand: with every box tabbed there's nothing to close.
     */
    private static void closeButton() {
        Panel close = Panel.builder(OpenAnywhere.MOD_ID + ":close_shown")
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> {
                            Tab front = Tabs.shownItem();
                            if (front != null) ClientPlayNetworking.send(new CloseC2S(front.id()));
                        })
                        .build())
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.around(com.trevorschoeny.openanywhere.api.ShownItem.SECTION, PanelPosition.pixel(() -> {
                    Reference frame = frame(Minecraft.getInstance().gui.screen());
                    // Off screen when there's no frame (hidden then anyway).
                    if (frame == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(frame.leftPos() + frame.imageWidth() - CLOSE_SIZE / 2 - 1, frame.topPos() - CLOSE_SIZE / 2);
                })))
                .build()
                .visibleWhen(() -> Tabs.showing() && Tabs.shownItem() != null && Settings.tabs() == Settings.TabMode.HAND);
        new AnchorPanelAdapter(close);
    }

    /** The frame the item in front shows in: the host's container list, else the screen's own. */
    static @Nullable Reference frame(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return null;
        Reference list = ContainerRegion.CONTAINERS.box(screen);
        if (list != null) return list;
        ScreenAccessor edge = (ScreenAccessor) container;
        return new Reference(edge.openanywhere$leftPos(), edge.openanywhere$topPos(),
                edge.openanywhere$imageWidth(), edge.openanywhere$imageHeight());
    }

    /** Where the item in front came from, for the self-checks: its path's source. */
    static ItemRef.@Nullable Source sourceOfShown() {
        Tab front = Tabs.shownItem();
        return front == null || front.id().path() == null ? null : front.id().path().source();
    }
}
