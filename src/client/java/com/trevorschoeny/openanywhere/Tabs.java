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
 * <p>Each tab is a section that is <i>present</i> (its container is open here); the one
 * <i>shown</i> is the pick while it's present, else the screen's own container, else the
 * first present in order. The row shows only with two or more present: one container needs
 * no picking.
 */
public final class Tabs {

    private Tabs() {}

    private record Tab(String id, int order, BooleanSupplier present) {}

    private static final List<Tab> TABS = new ArrayList<>();

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
        TABS.add(new Tab(id, order, present));
        TABS.sort(Comparator.comparingInt(Tab::order).thenComparing(Tab::id));
        ContainerTabs.tab(id, ModeScope.BOTH)
                .icon(icon)
                .tooltip(tooltip)
                .on(ContainerScreen.class)
                .order(order)
                .visibleWhen(() -> present.getAsBoolean() && row().size() > 1)
                .selectedWhen(() -> id.equals(shown()))
                .onClick(() -> pick(id))
                .declare();
        return () -> present.getAsBoolean() && id.equals(shown());
    }

    /** Shows section {@code id} on the open screen: a click, the wheel, an open, a shift-click landing in it. */
    public static void pick(String id) {
        var player = Minecraft.getInstance().player;
        pick = id;
        pickedOn = player == null ? null : player.containerMenu;
    }

    /** The present sections, in row order. */
    // ponytail: every tab's visibleWhen walks the whole list each frame (a few dozen cheap reads); cache per frame if it ever shows in a profile.
    public static List<String> row() {
        List<String> out = new ArrayList<>();
        for (Tab t : TABS) if (t.present().getAsBoolean()) out.add(t.id());
        return out;
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
