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
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotGroupElement;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * What this mod adds to a chest's screen (or the host's, {@link ContainerHost}): the
 * screen's own container and each opened item as sections of MenuKit's container list, one
 * shown at a time by tabs ({@link Tabs}), and a close button for the item shown.
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
        SlotScroll.register();
        TabWheel.register();
        containerList();
        com.trevorschoeny.openanywhere.api.ShownItem.declare();
    }

    /**
     * On a chest's screen, the chest and each opened item are sections of MenuKit's
     * container list, one shown at a time, picked by tabs. Each section's
     * content is one SlotKit group: the chest's own, or the one the server added to the menu
     * when the item opened. MenuKit makes an item's section and tab when it opens and drops
     * them when it closes, so any number can be open.
     */
    private static void containerList() {
        // One container at a time, picked by tabs on the panel's top edge, the panel fitting
        // the one shown. The pick is ours (Tabs); MenuKit draws the row.
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
     * An opened item's tab tooltip: its name, then where it sits, in grey:
     * "In Large Chest" (the screen's own container, by its title), "In
     * Inventory", or "In Ender Chest".
     */
    static Component tooltip(Component name, @Nullable Session session) {
        Source source = session == null ? null : sourceOf(session);
        if (source == null) return name;
        Component where = switch (source) {
            case CONTAINER -> screenTitle();
            case ENDER_CHEST -> Component.translatable("container.enderchest");
            case INVENTORY -> Component.translatable("container.inventory");
        };
        return MKTooltip.joinLines(List.of(name, Component.translatable("openanywhere.tab.in", where).withStyle(ChatFormatting.GRAY)));
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
        Block block = source == null || source.block() == null ? null : BuiltInRegistries.BLOCK.getValue(source.block());
        return block == null ? new ItemStack(Items.CHEST) : new ItemStack(block);
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
        for (Session s : Sessions.of(player)) if (shown.equals(Tabs.idOf(s))) return s;
        return null;
    }

    /**
     * The shown item's close button: a raised button with a black "x" in its own unstyled
     * panel, centred on the container panel's top-right corner, so it
     * sits over the frame's edge and never over a slot or a tab, and the whole button takes
     * clicks. One button for whichever item is shown; none while the screen's own container is.
     */
    private static void shownClose() {
        Panel close = Panel.builder(OpenAnywhere.MOD_ID + ":close_shown")
                .add(Button.builder()
                        .icon(CLOSE_ICON)
                        .size(CLOSE_SIZE, CLOSE_SIZE)
                        .onClick(() -> {
                            Session open = shownSession();
                            if (open != null) ClientPlayNetworking.send(new CloseC2S(open.kind(), open.lane()));
                        })
                        .build())
                .style(PanelStyle.NONE)
                // Both modes, like the session it closes: one opened in survival outlives a
                // /gamemode switch.
                .in(ModeScope.BOTH, Placement.around(ContainerRegion.CONTAINERS, PanelPosition.pixel(() -> {
                    var screen = Minecraft.getInstance().gui.screen();
                    Reference list = screen == null ? null : ContainerRegion.CONTAINERS.box(screen);
                    // Off screen when there's no list (hidden then anyway).
                    if (list == null) return new ScreenOrigin(-CLOSE_SIZE, -CLOSE_SIZE);
                    return new ScreenOrigin(list.leftPos() + list.imageWidth() - CLOSE_SIZE / 2 - 1, list.topPos() - CLOSE_SIZE / 2);
                })))
                .build()
                .visibleWhen(() -> shownSession() != null);
        new AnchorPanelAdapter(close);
    }
}
