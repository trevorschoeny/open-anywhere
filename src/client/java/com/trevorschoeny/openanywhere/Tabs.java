package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * One container at a time on a chest's screen, picked by a row of tabs on the list's top
 * edge (Trev, 2026-10-21). MenuKit draws the row, makes a section and tab for each opened
 * item ({@link ContainerRegion#sections}) and fits the frame; which one is picked, the
 * fallback when the picked one goes away and the wheel are ours.
 *
 * <p>The row is the screen's own container (the host has none), then what's open, in the order it opened
 * (Trev, 2026-10-22): one closed and opened again goes to the end. That's the order of
 * the player's sessions, which MenuKit is given as the keys. The row shows only with two
 * or more: one container needs no picking.
 */
public final class Tabs {

    private Tabs() {}

    /** The kind of section every opened item gets one of. */
    static final String KIND = OpenAnywhere.MOD_ID + ":opened";

    /** An opened item, as MenuKit keys its section: the kind and lane, {@code shulker_0}. */
    record Key(Openable kind, int lane) {
        @Override public String toString() {
            return kind.name().toLowerCase(Locale.ROOT) + "_" + lane;
        }
    }

    /** The picked section, and the menu it was picked on: a pick never outlives its screen. */
    private static @Nullable String pick;
    private static @Nullable AbstractContainerMenu pickedOn;

    /** On a chest's screen the list and inventory are one frame, fitted to the shown section. */
    static void fit() {
        ContainerRegion.fitted(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));
    }

    /** The section id MenuKit gives {@code key}'s section: {@code openanywhere:opened#shulker_0}. */
    static String idOf(Key key) {
        return KIND + "#" + key;
    }

    /** The section {@code session} shows in. */
    public static String idOf(Session session) {
        return idOf(new Key(session.kind(), session.lane()));
    }

    /** What's open on {@code screen}'s menu, in the order it opened: MenuKit's keys. */
    static List<Key> keys(AbstractContainerScreen<?> screen) {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        List<Key> keys = new ArrayList<>();
        for (Session s : Sessions.of(player)) if (s.stacked() && s.menu() == screen.getMenu()) keys.add(new Key(s.kind(), s.lane()));
        return keys;
    }

    /** Shows section {@code id} on the open screen: a click, the wheel, an open, a shift-click landing in it. */
    public static void pick(String id) {
        var player = Minecraft.getInstance().player;
        pick = id;
        pickedOn = player == null ? null : player.containerMenu;
    }

    /** The sections on the open screen, as the row reads: the screen's own container, then what opened, in order. */
    public static List<String> row() {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return List.of();
        List<Key> keys = keys(screen);
        if (keys.isEmpty()) return List.of();
        List<String> row = new ArrayList<>();
        // The host has no container of its own, only what opened on it.
        if (!ContainerHost.is(screen.getMenu())) row.add(ClientPanels.CHEST_ID);
        for (Key k : keys) row.add(idOf(k));
        return row;
    }

    /**
     * For the self-checks: whether section {@code id} drew in the last frames. The chest's
     * by its MenuKit anchor; an opened item's by its grid, since MenuKit has no anchor for
     * a section made per key.
     */
    public static boolean drawnForChecks(String id) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return false;
        if (id.equals(ClientPanels.CHEST_ID)) return ContainerRegion.anchor(id).box(screen) != null;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        for (Session s : Sessions.of(player)) {
            if (s.stacked() && id.equals(idOf(s)))
                return net.minecraft.util.Util.getMillis() - SlotScroll.drawnAt(Groups.id(s.kind(), s.lane())) < 100;
        }
        return false;
    }

    /** The section shown now: the pick if still there, else the first: the screen's own container, if it has one. */
    public static @Nullable String shown() {
        List<String> row = row();
        var player = Minecraft.getInstance().player;
        if (pick != null && player != null && pickedOn == player.containerMenu && row.contains(pick)) return pick;
        return row.isEmpty() ? null : row.getFirst();
    }
}
