package com.trevorschoeny.openanywhere;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.SlotItemLooks;
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
 * a box or ender chest plays its open animation and stays open (greyed instead, with the
 * animation turned off), a bundle drawn open, and an open bundle's add slot marked with a plus.
 */
public final class ClientMarks {

    private ClientMarks() {}

    /** Whether {@code slot} holds an item open, as the player should see it. */
    private static boolean shownOpen(Slot slot) {
        return Tabs.looksOpen(slot);
    }

    /**
     * Where an opened item sits, as the server names it: what its animation is keyed by, so a
     * screen opened in place of another picks it up where it stood.
     */

    /**
     * A quarter second, as a placed shulker box takes to open. The property slows it for a
     * frame-by-frame capture, whose screenshots each take longer than the whole animation.
     */
    static final Transition OPENING = Transition.of(Integer.getInteger("openanywhere.openMillis", 250), Easing.EASE_OUT);
    /**
     * The lid opening, the box shrinking as it goes and sitting a little lower, so the open
     * lid stays inside the slot.
     */
    static final ItemAnimation OPEN = ItemAnimation.OPEN_LID
            .posed(t -> new ItemAnimation.Pose(1f - 0.3f * t, 0f, 2f * t));

    /**
     * Boxes and ender chests open in their slots, and close again, through MenuKit's slot
     * looks. A bundle has no lid; it's drawn open by {@link #drawnAs}.
     */
    static void register() {
        SlotItemLooks.declare(OpenAnywhere.MOD_ID + ":open_items", (screen, slot, stack) -> {
            Openable kind = Openable.of(stack);
            if (kind == null || kind == Openable.BUNDLE || !Settings.animation()) return null;
            var place = Tabs.pathOf(slot);
            if (place == null) return null;
            float t = OPENING.toward(place, shownOpen(slot));
            return t > 0 ? OPEN.at(t) : null;
        });
    }

    /** Shulker Palette's grey for its open box: a disabled control's. */
    private static final int GREY = 0x80606060;
    /** MenuKit's add mark (ItemPicker): white over vanilla's shadow for white. */
    private static final int ADD_MARK = 0xFFFFFFFF, ADD_SHADOW = 0xFF3F3F3F;

    public static void draw(GuiGraphicsExtractor graphics, Slot slot, @Nullable CreatedSlot created) {
        // With the animation on, an open box shows by its lid and a bundle by its open bag.
        // Grey, too, every slot of the item in front while it's on the cursor: locked till it's put down.
        if (shownOpen(slot) && !Settings.animation() || Tabs.frozen() && !(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
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
        return openBundle(stack);
    }

    /** {@code bundle} with vanilla's open-bag art for its colour. */
    static ItemStack openBundle(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ItemStack drawn = stack.copy();
        drawn.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, path + "_open"));
        return drawn;
    }

    private static boolean isAddSlot(Slot slot, CreatedSlot created) {
        if (Groups.bundleIndex(slot) < 0) return false;
        int add = BundleView.addSlot(BundleView.contents(Minecraft.getInstance().player));
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
