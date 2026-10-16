package com.trevorschoeny.shulkersininventory;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector2i;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The tooltip view's input: scroll over a shulker box or ender chest to select a stack
 * in its grid. The right-click that takes it is {@code TakeSelectedMixin}.
 *
 * <p>Vanilla's own mechanism for exactly this: a screen keeps a list of
 * {@link ItemSlotMouseAction}s, and bundles scroll through one ({@code BundleMouseActions}).
 * This mirrors it. The screen offers the wheel to these before Mouse Tweaks sees it, so
 * over a shulker or ender chest the wheel selects, as it does over a bundle.
 *
 * <p>Scrolling steps through the filled cells only; empty ones have nothing to take.
 */
public final class ContainerMouseActions implements ItemSlotMouseAction {

    private final ScrollWheelHandler scrollWheel = new ScrollWheelHandler();

    @Override
    public boolean matches(Slot slot) {
        ItemStack stack = slot.getItem();
        // A stack of shulker boxes (Carpet) can't be taken from: they share one contents list.
        if (Openable.isShulker(stack)) return stack.getCount() == 1 && ClientView.shulkerGrid();
        return Openable.isEnderChest(stack) && ClientView.enderGrid();
    }

    @Override
    public boolean onMouseScrolled(double scrollX, double scrollY, int slotIndex, ItemStack stack) {
        List<Integer> filled = filledIndices(ClientGrids.itemsOf(stack));
        if (filled.isEmpty()) return false;

        // Same reading of the wheel as vanilla's bundle: vertical, else horizontal reversed.
        Vector2i wheel = scrollWheel.onMouseScroll(scrollX, scrollY);
        int delta = wheel.y == 0 ? -wheel.x : wheel.y;
        if (delta == 0) return true;

        var player = Minecraft.getInstance().player;
        Selection.Entry current = Selection.get(player);
        int position = current == null || current.slot() != slotIndex ? -1 : filled.indexOf(current.index());
        int next = ScrollWheelHandler.getNextScrollWheelSelection(delta, position, filled.size());
        select(slotIndex, filled.get(next));
        return true;
    }

    @Override
    public void onStopHovering(Slot slot) {
        if (Selection.get(Minecraft.getInstance().player) != null) select(slot.index, -1);
    }

    /** As vanilla's bundle: a shift-click or a number-key swap drops the selection. */
    @Override
    public void onSlotClicked(Slot slot, ContainerInput input) {
        if (input == ContainerInput.QUICK_MOVE || input == ContainerInput.SWAP) onStopHovering(slot);
    }

    /** Sets the selection here and on the server, in the order the click will read it. */
    private static void select(int slot, int index) {
        Selection.set(Minecraft.getInstance().player, slot, index);
        ClientPlayNetworking.send(new SelectC2S(slot, index));
    }

    private static List<Integer> filledIndices(List<ItemStack> items) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) filled.add(i);
        return filled;
    }
}
