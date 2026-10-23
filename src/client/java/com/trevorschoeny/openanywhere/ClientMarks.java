package com.trevorschoeny.openanywhere;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * How an opened item looks in its slot ({@code SlotMarksMixin}, {@code OpenBundleLookMixin}):
 * greyed, a bundle drawn open, and an open bundle's add slot marked with a plus.
 */
public final class ClientMarks {

    private ClientMarks() {}

    /** Items just opened whose tab hasn't drawn yet, and since when. */
    private record Awaiting(Session session, long since) {}
    private static final Map<String, Awaiting> AWAITING = new HashMap<>();
    // However it went, an entry goes after this long, so one whose tab never drew can't keep the grey off.
    private static final long AWAIT_MS = 300;

    /**
     * {@code session} just opened: its slot isn't marked open until its tab draws. MenuKit
     * shows the tab a frame later; until then the slot looks as it did before the click,
     * instead of flashing the box greyed in the chest.
     */
    static void awaitTab(Session session) {
        AWAITING.put(Tabs.idOf(session), new Awaiting(session, Util.getMillis()));
    }

    /** For the self-checks: {@link #awaitingTab}. */
    public static boolean awaitingTabForChecks(Slot slot) {
        return awaitingTab(slot);
    }

    /** True while {@code slot} holds an item just opened whose tab hasn't drawn yet. */
    private static boolean awaitingTab(Slot slot) {
        if (AWAITING.isEmpty()) return false;
        boolean onScreen = Minecraft.getInstance().gui.screen() != null;
        long now = Util.getMillis();
        AWAITING.values().removeIf(a -> !onScreen || now - a.since() > AWAIT_MS
                || SlotScroll.drawnAt(Groups.id(a.session().kind(), a.session().lane())) >= a.since());
        for (Awaiting a : AWAITING.values()) {
            if (a.session().container() == slot.container && a.session().containerSlot() == slot.getContainerSlot()) return true;
        }
        return false;
    }

    /** Whether {@code slot} holds an item open, as the player should see it. */
    private static boolean shownOpen(Slot slot) {
        return Sessions.isOpenOnClient(slot.container, slot.getContainerSlot()) && !awaitingTab(slot);
    }

    /** Shulker Palette's grey for its open box: a disabled control's. */
    private static final int GREY = 0x80606060;
    /** MenuKit's add mark (ItemPicker): white over vanilla's shadow for white. */
    private static final int ADD_MARK = 0xFFFFFFFF, ADD_SHADOW = 0xFF3F3F3F;

    public static void draw(GuiGraphicsExtractor graphics, Slot slot, @Nullable CreatedSlot created) {
        if (shownOpen(slot)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREY);
        }
        if (created != null && !slot.hasItem() && isAddSlot(slot, created)) {
            // MenuKit measures from the slot's 18 px frame, one pixel up and left of the item.
            int frameX = slot.x - 1, frameY = slot.y - 1;
            plus(graphics, frameX + 1, frameY + 1, ADD_SHADOW);
            plus(graphics, frameX, frameY, ADD_MARK);
        }
    }

    /**
     * The stack to draw in {@code slot}: an open bundle with vanilla's open-bag art for its
     * colour ({@code assets/openanywhere/items/<colour>_bundle_open.json}), else the stack.
     */
    public static ItemStack drawnAs(Slot slot, ItemStack stack) {
        if (Openable.of(stack) != Openable.BUNDLE || !shownOpen(slot)) return stack;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ItemStack drawn = stack.copy();
        drawn.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, path + "_open"));
        return drawn;
    }

    private static boolean isAddSlot(Slot slot, CreatedSlot created) {
        int lane = Groups.laneOf(slot, Openable.BUNDLE);
        if (lane < 0) return false;
        int add = BundleView.addSlot(BundleView.contents(Minecraft.getInstance().player, lane));
        return add >= 0 && created.getLocalIndex() == add;
    }

    /**
     * A 6 x 6 plus of 2 px bars, centred in the 18 px slot frame at {@code (x, y)}: MenuKit's
     * {@code ItemPicker.drawAddMark}, which is package-private, so the same two fills.
     */
    private static void plus(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 6, y + 8, x + 12, y + 10, color);
        g.fill(x + 8, y + 6, x + 10, y + 12, color);
    }
}
