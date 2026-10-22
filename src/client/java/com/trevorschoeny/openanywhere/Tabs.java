package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * One container at a time on a chest's screen, picked by a row of tabs on the list's top
 * edge (Trev, 2026-10-21). MenuKit draws the row and fits the frame; which one is picked,
 * what a click picks, the fallback when the picked one goes away and the wheel are ours.
 *
 * <p>Each section is <i>present</i> while its container is open here; the one <i>shown</i>
 * is the pick while it's present, else the screen's own container, else the first present.
 * The row shows only with two or more present: one container needs no picking.
 *
 * <p>The row is in the order things opened (Trev, 2026-10-22): one closed and opened again
 * goes to the end, wherever it was. MenuKit orders tabs by a fixed number, so the tabs
 * declared to it are places in the row, not sections: place {@code i} shows whatever is
 * {@code i}th in {@link #row()}, its icon, tooltip, pick and click.
 */
public final class Tabs {

    private Tabs() {}

    private record Tab(String id, int order, BooleanSupplier present, Supplier<ItemStack> icon,
                       Supplier<@Nullable Component> tooltip) {}

    private static final List<Tab> TABS = new ArrayList<>();

    /** The present sections in the order they came, for the menu {@link #orderOn}. */
    private static final List<String> ORDER = new ArrayList<>();
    private static @Nullable AbstractContainerMenu orderOn;

    /** The picked section, and the menu it was picked on: a pick never outlives its screen. */
    private static @Nullable String pick;
    private static @Nullable AbstractContainerMenu pickedOn;

    /** On a chest's screen the list and inventory are one frame, fitted to the shown section. */
    static void fit() {
        ContainerRegion.fitted(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));
    }

    /**
     * Declares section {@code id}'s tab, and gives back what the section's
     * {@code visibleWhen} should be: present and shown.
     */
    static BooleanSupplier tab(String id, int order, BooleanSupplier present,
                               Supplier<ItemStack> icon, Supplier<@Nullable Component> tooltip) {
        TABS.add(new Tab(id, order, present, icon, tooltip));
        TABS.sort(Comparator.comparingInt(Tab::order).thenComparing(Tab::id));
        // One more place in the row: as many places as sections.
        int place = TABS.size() - 1;
        ContainerTabs.tab(OpenAnywhere.MOD_ID + ":tab_" + place, ModeScope.BOTH)
                .icon(() -> {
                    Tab t = at(place);
                    return t == null ? ItemStack.EMPTY : t.icon().get();
                })
                .tooltip(() -> {
                    Tab t = at(place);
                    return t == null ? null : t.tooltip().get();
                })
                .on(ContainerScreen.class)
                .order(place)
                .visibleWhen(() -> row().size() > 1 && at(place) != null)
                .selectedWhen(() -> {
                    Tab t = at(place);
                    return t != null && t.id().equals(shown());
                })
                .onClick(() -> {
                    Tab t = at(place);
                    if (t != null) pick(t.id());
                })
                .declare();
        return () -> present.getAsBoolean() && id.equals(shown());
    }

    /** The section in place {@code i} of the row, or {@code null} past its end. */
    private static @Nullable Tab at(int i) {
        List<String> row = row();
        if (i >= row.size()) return null;
        String id = row.get(i);
        for (Tab t : TABS) if (t.id().equals(id)) return t;
        return null;
    }

    /** Shows section {@code id} on the open screen: a click, the wheel, an open, a shift-click landing in it. */
    public static void pick(String id) {
        var player = Minecraft.getInstance().player;
        pick = id;
        pickedOn = player == null ? null : player.containerMenu;
    }

    /**
     * The present sections, in the order they came: one gone drops out, one new (or back)
     * goes on the end. A new screen starts over, its sections in declared order.
     */
    // ponytail: every place asks each frame, so this walks the sections a few dozen times a frame (cheap reads); cache per frame if it ever shows in a profile.
    public static List<String> row() {
        var player = Minecraft.getInstance().player;
        AbstractContainerMenu menu = player == null ? null : player.containerMenu;
        if (menu != orderOn) {
            ORDER.clear();
            orderOn = menu;
        }
        List<String> present = new ArrayList<>();
        for (Tab t : TABS) if (t.present().getAsBoolean()) present.add(t.id());
        ORDER.retainAll(present);
        for (String id : present) if (!ORDER.contains(id)) ORDER.add(id);
        return List.copyOf(ORDER);
    }

    /** The section shown now: the pick if still present here, else the screen's own container, else the first present. */
    public static @Nullable String shown() {
        List<String> row = row();
        var player = Minecraft.getInstance().player;
        if (pick != null && player != null && pickedOn == player.containerMenu && row.contains(pick)) return pick;
        if (row.contains(ClientPanels.CHEST_ID)) return ClientPanels.CHEST_ID;
        return row.isEmpty() ? null : row.getFirst();
    }
}
