package com.trevorschoeny.openanywhere;

import java.util.function.Supplier;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ContainerRegion;
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
     * Where an opened item came from, which decides where its section sits: under the
     * screen's own container, under the ender chest's section, or after both, for an item
     * opened from the player's inventory (Trev, 2026-10-21: what opened from what).
     */
    enum Source {
        CONTAINER(100), ENDER_CHEST(300), INVENTORY(500);

        /** The first order in the column for this source's sections. */
        final int order;

        Source(int order) {
            this.order = order;
        }
    }

    /** The screen's own container's section. */
    static final String CHEST_ID = OpenAnywhere.MOD_ID + ":chest";

    /** Lane {@code lane}'s section id under {@code source}: {@code openanywhere:shulker_0_container}. */
    static String sectionId(Openable kind, int lane, Source source) {
        return kind.panelId(lane) + "_" + source.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Where lane {@code lane}'s section sits in the column under {@code source}. */
    static int orderOf(Openable kind, int lane, Source source) {
        if (kind == Openable.ENDER_CHEST) return Source.ENDER_CHEST.order;
        // Boxes, then bundles, each in lane order, after their source's container.
        return source.order + 1 + (kind == Openable.SHULKER ? 0 : 50) + lane;
    }

    static void register() {
        closeButton("native", ClientPanels::nativeSession, () -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
            ScreenAccessor frame = (ScreenAccessor) screen;
            return new Reference(frame.openanywhere$leftPos(), frame.openanywhere$topPos(), frame.openanywhere$imageWidth(), 0);
        });
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
     * On a chest's screen, opened items stack below the chest as one column in MenuKit's
     * container list, with the inventory in its own panel below (Trev, 2026-10-21). Each
     * section's content is one SlotKit group, found by its id on the open menu: the chest's
     * own, then the ender chest's, then each lane's. Each lane has a section per source,
     * shown only while the item in the lane came from there, so the column reads: the chest
     * and what opened from it, the ender chest and what opened from it, then what opened
     * from the inventory. The list grows, then centres, then scrolls; that and the wheel
     * and resource packs are MenuKit's.
     */
    private static void containerList() {
        // One container at a time, picked by tabs on the panel's top edge, the panel fitting
        // the one shown (Trev, 2026-10-21). MenuKit's tabs mode does all of it.
        ContainerRegion.tabs(ModeScope.BOTH).on(ContainerScreen.class);
        // No source: the screen's own container, so MenuKit names and dresses it from the
        // block the screen was opened on. Shown only with an item open below it; otherwise
        // the screen is vanilla's own.
        ContainerRegion.section(CHEST_ID, ModeScope.BOTH)
                .title(() -> Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen
                        ? screen.getTitle() : Component.translatable("container.chest"))
                .content(SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE)))
                .on(ContainerScreen.class)
                .visibleWhen(ClientPanels::anyOpenHere)
                .declare();
        // Asked for now, while MenuKit takes declarations: read later to tell whether it shows.
        ContainerRegion.anchor(CHEST_ID);

        // The ender chest opened from its item. Its source is the item, so MenuKit names it
        // "Ender Chest" and gives it the chest's art, as a placed one has.
        String ender = Openable.ENDER_CHEST.panelId(0);
        ContainerRegion.section(ender, ModeScope.BOTH)
                .title(() -> Component.translatable("container.enderchest"))
                .source(() -> {
                    Session s = clientSession(Openable.ENDER_CHEST, 0);
                    return s == null || s.stack().isEmpty() ? null : ContainerRegion.Source.of(s.stack());
                })
                .content(SlotGroupElement.of(Groups.group(Openable.ENDER_CHEST, 0).id()))
                .on(ContainerScreen.class)
                .order(Source.ENDER_CHEST.order)
                .visibleWhen(() -> clientSession(Openable.ENDER_CHEST, 0) != null)
                .declare();
        sectionClose(ender, Openable.ENDER_CHEST, 0);

        for (Openable kind : new Openable[] {Openable.SHULKER, Openable.BUNDLE}) {
            for (int lane = 0; lane < kind.lanes; lane++) {
                for (Source source : Source.values()) itemSection(kind, lane, source);
            }
        }
    }

    /** Lane {@code lane}'s section under {@code source}, shown while its item came from there. */
    private static void itemSection(Openable kind, int lane, Source source) {
        String id = sectionId(kind, lane, source);
        Component plain = kind == Openable.SHULKER ? Component.translatable("container.shulkerBox")
                : Component.translatable("item.minecraft.bundle");
        var section = ContainerRegion.section(id, ModeScope.BOTH)
                .title(() -> title(kind, lane, plain))
                .content(SlotGroupElement.of(Groups.group(kind, lane).id()))
                .on(ContainerScreen.class)
                .order(orderOf(kind, lane, source))
                .visibleWhen(() -> sourceOf(clientSession(kind, lane)) == source);
        if (kind == Openable.SHULKER) {
            section.source(() -> {
                Session s = clientSession(kind, lane);
                return s == null || s.stack().isEmpty() ? null : ContainerRegion.Source.of(s.stack());
            });
        } else {
            // A bundle isn't a block, so it gets no source; this is its name when two
            // sections need telling apart.
            section.name(() -> title(kind, lane, plain));
        }
        section.declare();
        sectionClose(id, kind, lane);
    }

    /** The section {@code session} shows in: the ender chest's own, or its lane's under where it came from. */
    static String sectionIdOf(Session session) {
        if (session.kind() == Openable.ENDER_CHEST) return Openable.ENDER_CHEST.panelId(0);
        Source source = sourceOf(session);
        return sectionId(session.kind(), session.lane(), source == null ? Source.INVENTORY : source);
    }

    /** Where the item open in {@code session} came from, read off its slot on the open menu. */
    static @Nullable Source sourceOf(@Nullable Session session) {
        if (session == null || session.menuSlot() < 0 || session.menuSlot() >= session.menu().slots.size()) return null;
        var slot = session.menu().getSlot(session.menuSlot());
        if (slot.container instanceof Inventory) return Source.INVENTORY;
        if (Groups.isIn(slot, Openable.ENDER_CHEST)) return Source.ENDER_CHEST;
        return Source.CONTAINER;
    }

    /** True with any section open on the screen's menu. */
    static boolean anyOpenHere() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;
        for (Session s : Sessions.of(mc.player)) if (s.stacked() && s.menu() == screen.getMenu()) return true;
        return false;
    }

    static @Nullable Session clientSession(Openable kind, int lane) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : Sessions.at(player, kind, lane);
    }

    /**
     * An opened item's title: its own name, as the inventory shows it ("Red Shulker Box", or
     * the name the player gave it), so several open boxes can be told apart; {@code plain}
     * until the item is known.
     */
    static Component title(Openable kind, int lane, Component plain) {
        Session s = clientSession(kind, lane);
        return s == null || s.stack().isEmpty() ? plain : s.stack().getHoverName();
    }

    /**
     * The shown section's close button, centred on the container panel's top-right corner,
     * as on vanilla's own screens this mod opens (Trev, 2026-10-16), so it sits over the
     * frame's edge and never over a slot or a tab. It rides on MenuKit's anchor for the
     * section: in tabs mode only the shown section's anchor is there, so only its X shows.
     */
    private static void sectionClose(String sectionId, Openable kind, int lane) {
        Anchor anchor = ContainerRegion.anchor(sectionId);
        Panel close = closePanel(sectionId.replace(':', '_'), () -> clientSession(kind, lane),
                Placement.around(anchor, PanelPosition.pixel(() -> {
                    var screen = Minecraft.getInstance().gui.screen();
                    Reference list = screen == null || anchor.box(screen) == null ? null : ContainerRegion.CONTAINERS.box(screen);
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
