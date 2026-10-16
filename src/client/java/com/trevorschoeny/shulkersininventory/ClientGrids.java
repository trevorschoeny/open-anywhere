package com.trevorschoeny.shulkersininventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** The 27 stacks an item's grid shows, read the same way by the tooltip and the scroll. */
public final class ClientGrids {

    private ClientGrids() {}

    /** The tooltip view's data for a hovered shulker box or ender chest. */
    public static ContainerTooltip tooltip(ItemStack stack) {
        return new ContainerTooltip(itemsOf(stack));
    }

    /** A shulker box's contents, or the ender chest copy the server sent. */
    static List<ItemStack> itemsOf(ItemStack stack) {
        if (Openable.isEnderChest(stack)) {
            Container ender = Openable.enderChest(Minecraft.getInstance().player);
            List<ItemStack> items = new ArrayList<>(ender.getContainerSize());
            for (int i = 0; i < ender.getContainerSize(); i++) items.add(ender.getItem(i));
            return items;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(Openable.GRID_SIZE, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }
}
