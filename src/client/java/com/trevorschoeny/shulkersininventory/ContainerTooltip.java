package com.trevorschoeny.shulkersininventory;

import java.util.List;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** The tooltip view's data: the 27 stacks of a shulker box or the ender chest, in slot order. */
public record ContainerTooltip(List<ItemStack> items) implements TooltipComponent {}
