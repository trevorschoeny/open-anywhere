package com.trevorschoeny.openanywhere;

import org.jspecify.annotations.Nullable;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** What {@code SlotMarksMixin} draws over a slot. */
public final class ClientMarks {

    private ClientMarks() {}

    /** Shulker Palette's grey for its open box: a disabled control's. */
    private static final int GREY = 0x80606060;
    /** MenuKit's add mark (ItemPicker): white over vanilla's shadow for white. */
    private static final int ADD_MARK = 0xFFFFFFFF, ADD_SHADOW = 0xFF3F3F3F;

    public static void draw(GuiGraphicsExtractor graphics, Slot slot, @Nullable CreatedSlot created) {
        if (Sessions.isOpenOnClient(slot.container, slot.getContainerSlot()) && !OpenAnywhereClient.awaitingTab(slot)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREY);
        }
        if (created != null && !slot.hasItem() && isAddSlot(created)) {
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
        if (Openable.of(stack) != Openable.BUNDLE || !Sessions.isOpenOnClient(slot.container, slot.getContainerSlot())
                || OpenAnywhereClient.awaitingTab(slot)) return stack;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ItemStack drawn = stack.copy();
        drawn.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, path + "_open"));
        return drawn;
    }

    private static boolean isAddSlot(CreatedSlot created) {
        for (int lane = 0; lane < Openable.BUNDLE.lanes; lane++) {
            if (!created.panelId().equals(Openable.BUNDLE.slotPanelId(lane))) continue;
            int add = BundleView.addSlot(BundleView.contents(Minecraft.getInstance().player, lane));
            return add >= 0 && created.getLocalIndex() == add;
        }
        return false;
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
