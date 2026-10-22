package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/** What {@code SlotMarksMixin} draws over a slot. */
public final class ClientMarks {

    private ClientMarks() {}

    /** Shulker Palette's grey for its open box: a disabled control's. */
    private static final int GREY = 0x80606060;
    /** MenuKit's add mark (ItemPicker): white over vanilla's shadow for white. */
    private static final int ADD_MARK = 0xFFFFFFFF, ADD_SHADOW = 0xFF3F3F3F;

    public static void draw(GuiGraphicsExtractor graphics, Slot slot, @Nullable CreatedSlot created) {
        if (Sessions.isOpenAt(slot.container, slot.getContainerSlot())) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREY);
        }
        if (created != null && !slot.hasItem() && isAddSlot(created)) {
            // MenuKit measures from the slot's 18 px frame, one pixel up and left of the item.
            int frameX = slot.x - 1, frameY = slot.y - 1;
            plus(graphics, frameX + 1, frameY + 1, ADD_SHADOW);
            plus(graphics, frameX, frameY, ADD_MARK);
        }
    }

    private static boolean isAddSlot(CreatedSlot created) {
        int add = BundleView.addSlot(BundleView.contents(Minecraft.getInstance().player));
        return add >= 0 && created.panelId().equals(Openable.BUNDLE.slotPanelId()) && created.getLocalIndex() == add;
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
