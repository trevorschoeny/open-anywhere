package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenFrames;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
 * What this mod adds to the screens: the tab row (the inventory, the world container, then
 * each opened item, through MenuKit's {@link ContainerTabs}), the bundle's slots on its
 * host, and the close button for the item in front.
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
    static final String ITEM_TABS = OpenAnywhere.MOD_ID + ":items";
    /** The open bundle's section on its host. */
    static final String BUNDLE_ID = OpenAnywhere.MOD_ID + ":bundle";

    /** The inventory first, then the world container, then the items, in the server's order. */
    private static final int INVENTORY_ORDER = 0, WORLD_ORDER = 100, ITEMS_ORDER = 200;

    /** The screens the row stands on: the inventory, a chest (and the bundle's host), a shulker box. */
    @SuppressWarnings("unchecked")
    private static final Class<? extends AbstractContainerScreen<?>>[] SCREENS =
            new Class[] { InventoryScreen.class, ContainerScreen.class, ShulkerBoxScreen.class };

    // The world container's look, kept from its own screen so its tab shows it from any screen.
    // Null until a world screen is seen: items can't be made before the game has bound them.
    private static @Nullable ItemStack worldIcon;
    private static Component worldTitle = Component.translatable("container.chest");

    static void register() {
        // The inventory and a shulker box both draw 166 tall, but the box's screen counts 167
        // (a blank row under its art), so vanilla centres the inventory a pixel lower. The
        // same blank row under the inventory's art puts the two frames, and their slots, in
        // one place, so nothing moves as the tabs switch between them.
        ScreenFrames.stretch(InventoryScreen.class, 166, 1);
        SlotScroll.register();
        TabWheel.register();
        tabs();
        bundle();
        closeButton();
        com.trevorschoeny.openanywhere.api.ShownItem.declare();
    }

    private static void tabs() {
        ContainerTabs.tab(INVENTORY_TAB, ModeScope.BOTH)
                .icon(ClientPanels::playerHead)
                .tooltip(() -> Component.translatable("container.inventory"))
                .on(SCREENS)
                .order(INVENTORY_ORDER)
                .visibleWhen(Tabs::showing)
                .selectedWhen(() -> Tabs.selected().equals(TabId.INVENTORY))
                .onClick(() -> Tabs.pick(TabId.INVENTORY))
                .declare();
        ContainerTabs.tab(WORLD_TAB, ModeScope.BOTH)
                .icon(ClientPanels::worldIcon)
                .tooltip(ClientPanels::worldTitle)
                .on(SCREENS)
                .order(WORLD_ORDER)
                .visibleWhen(Tabs::hasWorld)
                .selectedWhen(() -> Tabs.selected().equals(TabId.WORLD))
                .onClick(() -> Tabs.pick(TabId.WORLD))
                .declare();
        ContainerTabs.keyed(ITEM_TABS, ModeScope.BOTH, Tabs::items)
                .tab((id, tab) -> tab
                        .icon(() -> item(id))
                        .tooltip(() -> tooltip(id))
                        .selectedWhen(() -> Tabs.selected().equals(id))
                        .onClick(() -> Tabs.pick(id)))
                .on(SCREENS)
                .order(ITEMS_ORDER)
                .declare();
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

    /**
     * An item tab's tooltip: its own name, as the inventory shows it ("Red Shulker Box", or
     * the name the player gave it), then where it sits, in grey: "In Inventory", "In Large
     * Chest" (the world container, by its title), "In Ender Chest", or the item it's inside.
     */
    private static Component tooltip(TabId id) {
        Tab tab = Tabs.tab(id);
        if (tab == null || id.path() == null) return Component.empty();
        Component where = tab.in().orElseGet(() -> switch (id.path().source()) {
            case INVENTORY -> Component.translatable("container.inventory");
            case CONTAINER -> worldTitle();
            case ENDER_CHEST -> Component.translatable("container.enderchest");
        });
        return MKTooltip.joinLines(List.of(tab.item().getHoverName(),
                Component.translatable("openanywhere.tab.in", where).withStyle(ChatFormatting.GRAY)));
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
                .visibleWhen(() -> Tabs.showing() && Tabs.shownItem() != null && !Settings.everyBox());
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
