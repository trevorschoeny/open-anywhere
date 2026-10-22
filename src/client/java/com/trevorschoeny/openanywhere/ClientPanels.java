package com.trevorschoeny.openanywhere;

import java.util.function.Supplier;

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
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotGroupElement;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * What this mod adds to screens: the sections of MenuKit's container list (on a chest's
 * screen, and on the bundle's host, where the bundle is the only one), a close button on
 * each, and a close button on the corner of vanilla's own shulker box and ender chest
 * screens when this mod opened them.
 */
final class ClientPanels {

    private ClientPanels() {}

    /**
     * An 11 px raised button around a 7 px black "x" (MenuKit insets an icon 2 px). A
     * text label can't be this small: MenuKit pads a label to about 17 px each way.
     */
    private static final int CLOSE_SIZE = 11;
    private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "close");

    /**
     * Where an opened item came from: the screen's own container, the ender chest's
     * section, or the player's inventory. Its tab's tooltip says so.
     */
    enum Source { CONTAINER, ENDER_CHEST, INVENTORY }

    /** The screen's own container's section, and its tab. */
    static final String CHEST_ID = OpenAnywhere.MOD_ID + ":chest";

    /** The screen's own container first; then what opened, in the order it opened (the keys' order). */
    private static final int CHEST_ORDER = 100, OPENED_ORDER = 200;

    static void register() {
        closeButton("native", ClientPanels::nativeSession, () -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
            ScreenAccessor frame = (ScreenAccessor) screen;
            return new Reference(frame.openanywhere$leftPos(), frame.openanywhere$topPos(), frame.openanywhere$imageWidth(), 0);
        });
        SlotScroll.register();
        TabWheel.register();
        containerList();
    }

    /** The shulker box or ender chest the screen is, when this mod opened it as vanilla's own. */
    private static @Nullable Session nativeSession() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
        for (Session s : Sessions.of(mc.player)) if (!s.stacked() && s.menu() == screen.getMenu()) return s;
        return null;
    }

    /**
     * On a chest's screen, the chest and each opened item are sections of MenuKit's
     * container list, one shown at a time, picked by tabs (Trev, 2026-10-21). Each section's
     * content is one SlotKit group: the chest's own, or the one the server added to the menu
     * when the item opened. MenuKit makes an item's section and tab when it opens and drops
     * them when it closes, so any number can be open.
     */
    private static void containerList() {
        // One container at a time, picked by tabs on the panel's top edge, the panel fitting
        // the one shown (Trev, 2026-10-21). The pick is ours (Tabs); MenuKit draws the row.
        Tabs.fit();
        // No source: the screen's own container, so MenuKit names and dresses it from the
        // block the screen was opened on. Shown only with an item open; otherwise the screen
        // is vanilla's own.
        ContainerRegion.section(CHEST_ID, ModeScope.BOTH)
                .title(ClientPanels::screenTitle)
                // The screen's own grid: vanilla's slots, six rows at most, so it never needs the
                // grid scroll (and its vanilla slots don't present inside one).
                .content(SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE)))
                .on(ContainerScreen.class)
                .order(CHEST_ORDER)
                .visibleWhen(() -> CHEST_ID.equals(Tabs.shown()))
                .onHiddenLanding(() -> Tabs.pick(CHEST_ID))
                .declare();
        // Asked for now, while MenuKit takes declarations: read later to tell whether it shows.
        ContainerRegion.anchor(CHEST_ID);
        ContainerTabs.tab(CHEST_ID, ModeScope.BOTH)
                .icon(ClientPanels::screenBlock)
                .tooltip(ClientPanels::screenTitle)
                .on(ContainerScreen.class)
                .order(CHEST_ORDER)
                .visibleWhen(() -> Tabs.row().size() > 1 && Tabs.row().contains(CHEST_ID))
                .selectedWhen(() -> CHEST_ID.equals(Tabs.shown()))
                .onClick(() -> Tabs.pick(CHEST_ID))
                .declare();

        ContainerRegion.sections(Tabs.KIND, ModeScope.BOTH, Tabs::keys)
                .section((key, section) -> openedSection(key, section))
                .tab((key, tab) -> {
                    String id = Tabs.idOf(key);
                    tab.icon(() -> stackOf(key))
                            .tooltip(() -> tooltip(title(key), clientSession(key)))
                            .order(OPENED_ORDER)
                            .visibleWhen(() -> Tabs.row().size() > 1)
                            .selectedWhen(() -> id.equals(Tabs.shown()))
                            .onClick(() -> Tabs.pick(id));
                })
                .on(ContainerScreen.class)
                .declare();
        shownClose();
    }

    /** An opened item's section: its slots, its name, and (for a block) the block's look. */
    private static void openedSection(Tabs.Key key, ContainerRegion.Builder section) {
        String id = Tabs.idOf(key);
        section.title(() -> title(key))
                .content(SlotScroll.of(Groups.id(key.kind(), key.lane()), key.kind().slots, key.kind() == Openable.BUNDLE))
                .order(OPENED_ORDER)
                .visibleWhen(() -> id.equals(Tabs.shown()))
                .onHiddenLanding(() -> Tabs.pick(id));
        if (key.kind() == Openable.BUNDLE) {
            // A bundle isn't a block, so it gets no source; this is its name when two
            // sections need telling apart.
            section.name(() -> title(key));
        } else {
            // A box or the ender chest: MenuKit names it and gives it the block's art.
            section.source(() -> {
                ItemStack stack = stackOf(key);
                return stack.isEmpty() ? null : ContainerRegion.Source.of(stack);
            });
        }
    }

    /** The screen's title, the chest's tab tooltip and section title. */
    private static Component screenTitle() {
        return Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen
                ? screen.getTitle() : Component.translatable("container.chest");
    }

    /**
     * An opened item's tab tooltip: its name, then where it sits, in grey (Trev,
     * 2026-10-22): "In Large Chest" (the screen's own container, by its title), "In
     * Inventory", or "In Ender Chest".
     */
    static Component tooltip(Component name, @Nullable Session session) {
        Source source = session == null ? null : sourceOf(session);
        if (source == null) return name;
        Component where = switch (source) {
            case CONTAINER -> Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen
                    ? screen.getTitle() : Component.translatable("container.chest");
            case ENDER_CHEST -> Component.translatable("container.enderchest");
            case INVENTORY -> Component.translatable("container.inventory");
        };
        return MKTooltip.joinLines(java.util.List.of(name,
                Component.translatable("openanywhere.tab.in", where).withStyle(net.minecraft.ChatFormatting.GRAY)));
    }

    /** Where the item open in {@code session} came from, read off its slot on the open menu. */
    static @Nullable Source sourceOf(@Nullable Session session) {
        if (session == null || session.menuSlot() < 0 || session.menuSlot() >= session.menu().slots.size()) return null;
        var slot = session.menu().getSlot(session.menuSlot());
        if (slot.container instanceof Inventory) return Source.INVENTORY;
        if (Groups.isIn(slot, Openable.ENDER_CHEST)) return Source.ENDER_CHEST;
        return Source.CONTAINER;
    }

    /** The item open as {@code key}, its tab's icon. */
    private static ItemStack stackOf(Tabs.Key key) {
        Session s = clientSession(key);
        return s == null ? ItemStack.EMPTY : s.stack();
    }

    /**
     * The screen's own container's tab icon: the block the screen was opened on
     * ({@link ContainerRegion#openedOn()}), when it has one; a chest otherwise (a minecart,
     * a screen opened by a key, a double chest).
     */
    private static ItemStack screenBlock() {
        ContainerRegion.Source source = ContainerRegion.openedOn();
        net.minecraft.world.level.block.Block block = source == null || source.block() == null
                ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(source.block());
        return block == null ? new ItemStack(net.minecraft.world.item.Items.CHEST) : new ItemStack(block);
    }

    static @Nullable Session clientSession(Tabs.Key key) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : Sessions.at(player, key.kind(), key.lane());
    }

    /**
     * An opened item's title: its own name, as the inventory shows it ("Red Shulker Box", or
     * the name the player gave it), so several open boxes can be told apart; the kind's
     * plain name until the item is known.
     */
    static Component title(Tabs.Key key) {
        ItemStack stack = stackOf(key);
        if (!stack.isEmpty()) return stack.getHoverName();
        return Component.translatable(switch (key.kind()) {
            case SHULKER -> "container.shulkerBox";
            case BUNDLE -> "item.minecraft.bundle";
            case ENDER_CHEST -> "container.enderchest";
        });
    }

    /** The session whose section shows now, or null when it's the screen's own container. */
    private static @Nullable Session shownSession() {
        var player = Minecraft.getInstance().player;
        String shown = Tabs.shown();
        if (player == null || shown == null) return null;
        for (Session s : Sessions.of(player)) if (s.stacked() && shown.equals(Tabs.idOf(s))) return s;
        return null;
    }

    /**
     * The shown item's close button, centred on the container panel's top-right corner,
     * as on vanilla's own screens this mod opens (Trev, 2026-10-16), so it sits over the
     * frame's edge and never over a slot or a tab. One button for whichever item is shown;
     * none while the screen's own container is.
     */
    private static void shownClose() {
        Panel close = closePanel("shown", ClientPanels::shownSession,
                Placement.around(ContainerRegion.CONTAINERS, PanelPosition.pixel(() -> {
                    var screen = Minecraft.getInstance().gui.screen();
                    Reference list = screen == null ? null : ContainerRegion.CONTAINERS.box(screen);
                    if (list == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(list.leftPos() + list.imageWidth() - CLOSE_SIZE / 2 - 1, list.topPos() - CLOSE_SIZE / 2);
                })));
        new AnchorPanelAdapter(close);
    }

    /**
     * A raised button with a black "x", in its own unstyled panel centred on the top-right
     * corner of {@code box}, so it sits over both edges and the whole button takes clicks
     * (Trev, 2026-10-16). Shown while {@code session} has one; a click closes that one.
     */
    private static void closeButton(String name, Supplier<@Nullable Session> session, Supplier<@Nullable Reference> box) {
        Panel close = closePanel(name, session, Placement.screen(PanelPosition.pixel(() -> {
            Reference b = box.get();
            // Off screen when there's nothing to close (hidden then anyway).
            if (b == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
            return new ScreenOrigin(b.leftPos() + b.imageWidth() - CLOSE_SIZE / 2, b.topPos() - CLOSE_SIZE / 2);
        })));
        new ScreenPanelAdapter(close, 0).onMatching(ScreenMatcher.all());
    }

    private static Panel closePanel(String name, Supplier<@Nullable Session> session, Placement placement) {
        return Panel.builder(OpenAnywhere.MOD_ID + ":close_" + name)
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> {
                            Session open = session.get();
                            if (open != null) ClientPlayNetworking.send(new CloseC2S(open.kind(), open.lane()));
                        })
                        .build())
                .style(PanelStyle.NONE)
                // Both modes, like the session it closes: one opened in survival outlives a
                // /gamemode switch.
                .in(ModeScope.BOTH, placement)
                .build()
                .visibleWhen(() -> session.get() != null);
    }
}
