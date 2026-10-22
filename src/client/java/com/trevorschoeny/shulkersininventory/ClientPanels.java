package com.trevorschoeny.shulkersininventory;

import java.util.function.Supplier;

import java.util.function.BooleanSupplier;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.slot.ClientSlots;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotGroupElement;
import com.trevorschoeny.shulkersininventory.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * What this mod adds to screens: the close button on the screen's corner, the sections
 * of MenuKit's container list on a chest's screen, and the bundle's panel with its own
 * close button on the inventory screen.
 */
final class ClientPanels {

    private ClientPanels() {}

    private static final int PADDING = 7;
    /** A row of 9 vanilla slots (18 pixels each): the width the bundle panel wraps at. */
    private static final int ROW_WIDTH = 9 * 18;
    /**
     * An 11 px raised button around a 7 px black "x" (MenuKit insets an icon 2 px). A
     * text label can't be this small: MenuKit pads a label to about 17 px each way.
     */
    private static final int CLOSE_SIZE = 11;
    private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath(ShulkersInInventory.MOD_ID, "close");

    static void register() {
        // On vanilla's own screen, the close button centres on the screen frame's corner.
        closeButton("native", ClientPanels::nativeSession, () -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
            ScreenAccessor frame = (ScreenAccessor) screen;
            return new Reference(frame.shulkersininventory$leftPos(), frame.shulkersininventory$topPos(),
                    frame.shulkersininventory$imageWidth(), 0);
        });
        bundlePanel();
        containerList();
    }

    /**
     * What the close button on the screen's corner closes: an item open below the chest
     * first (the innermost), else the shulker box or ender chest the screen is. The
     * bundle on the inventory screen has its own panel and button.
     */
    private static @Nullable Session nativeSession() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
        Session own = null;
        for (Session s : Sessions.of(mc.player)) {
            if (s.menu() != screen.getMenu()) continue;
            if (s.stacked()) {
                if (!(screen instanceof InventoryScreen)) return s;
            } else {
                own = s;
            }
        }
        return own;
    }

    /**
     * On a chest's screen, an opened box or bundle stacks below the chest as one column in
     * MenuKit's container list, with the inventory in its own panel below (Trev,
     * 2026-10-21). Each section's content is one SlotKit group, found by its id on the open
     * menu: the chest's own (a single chest, a double chest or a barrel, any size), then
     * the opened item's. Each group is its own container, so sorting and shift-click act on
     * one at a time. The list grows, then centres, then scrolls; that and the wheel and
     * resource packs are MenuKit's.
     */
    private static void containerList() {
        BooleanSupplier itemOpen = () -> ClientSlots.isShown(Groups.shulker().id()) || ClientSlots.isShown(Groups.bundle().id());
        // No source: the screen's own container, so MenuKit names and dresses it from the
        // block the screen was opened on. Shown only with an item open below it; otherwise
        // the screen is vanilla's own.
        ContainerRegion.section(ShulkersInInventory.MOD_ID + ":chest", ModeScope.BOTH)
                .title(() -> Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen
                        ? screen.getTitle() : Component.translatable("container.chest"))
                .content(SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE)))
                .on(ContainerScreen.class)
                .visibleWhen(itemOpen)
                .declare();
        // The opened item, below the chest. Each shows exactly while its group's slots do;
        // the bundle's grows a row at a time with its active count.
        ContainerRegion.section(Openable.SHULKER.panelId(), ModeScope.BOTH)
                .title(() -> title(Openable.SHULKER, Component.translatable("container.shulkerBox")))
                .source(() -> {
                    Session s = clientSession(Openable.SHULKER);
                    return s == null || s.stack().isEmpty() ? null : ContainerRegion.Source.of(s.stack());
                })
                .content(SlotGroupElement.of(Groups.shulker().id()))
                .on(ContainerScreen.class)
                .order(10)
                .declare();
        ContainerRegion.section(Openable.BUNDLE.panelId(), ModeScope.BOTH)
                .title(() -> title(Openable.BUNDLE, Component.translatable("item.minecraft.bundle")))
                .name(() -> title(Openable.BUNDLE, Component.translatable("item.minecraft.bundle")))
                .content(SlotGroupElement.of(Groups.bundle().id()))
                .on(ContainerScreen.class)
                .order(10)
                .declare();
    }

    private static @Nullable Session clientSession(Openable kind) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : Sessions.get(player, kind);
    }

    /** An opened item's title: the name the player gave it, else {@code plain}, vanilla's own. */
    private static Component title(Openable kind, Component plain) {
        Session s = clientSession(kind);
        return s != null && s.stack().has(DataComponents.CUSTOM_NAME) ? s.stack().getHoverName() : plain;
    }

    /**
     * The bundle on the inventory screen, where MenuKit's container list never is: its
     * slots in a panel above the frame, 9 to a row, shown while its slots are live. On a
     * chest's screen it is a section of the container list instead.
     */
    private static void bundlePanel() {
        ParityGroup group = Groups.bundle();
        Panel panel = Panel.builder(Openable.BUNDLE.panelId())
                .add(SlotGroupElement.of(group.id()))
                .style(PanelStyle.RAISED)
                // Both modes: it shows only with a bundle open, and an open bundle outlives a
                // /gamemode switch, so its place must too.
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10)))
                .build()
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> ClientSlots.isShown(group.id()));
        new ScreenPanelAdapter(panel, PADDING).onPlayerInventory();
        var mc = Minecraft.getInstance();
        closeButton("bundle",
                () -> mc.player == null || !panel.isVisible() ? null : Sessions.get(mc.player, Openable.BUNDLE),
                panel::placedBounds);
    }

    /**
     * A raised button with a black "x", in its own unstyled panel centred on the top-right
     * corner of {@code box}, so it sits over both edges and the whole button takes clicks
     * (Trev, 2026-10-16). Shown while {@code session} has one; a click closes that one.
     */
    private static void closeButton(String name, Supplier<@Nullable Session> session, Supplier<@Nullable Reference> box) {
        Panel close = Panel.builder(ShulkersInInventory.MOD_ID + ":close_" + name)
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> {
                            Session open = session.get();
                            if (open != null) ClientPlayNetworking.send(new CloseC2S(open.kind()));
                        })
                        .build())
                .style(PanelStyle.NONE)
                // Both modes, like the session it closes (see the bundle panel).
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> {
                    Reference b = box.get();
                    // Off screen when there's nothing to close (hidden then anyway).
                    if (b == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(b.leftPos() + b.imageWidth() - CLOSE_SIZE / 2, b.topPos() - CLOSE_SIZE / 2);
                })))
                .build()
                .visibleWhen(() -> session.get() != null);
        new ScreenPanelAdapter(close, 0).onMatching(ScreenMatcher.all());
    }
}
