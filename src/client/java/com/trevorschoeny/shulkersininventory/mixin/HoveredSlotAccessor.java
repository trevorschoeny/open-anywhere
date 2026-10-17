package com.trevorschoeny.shulkersininventory.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** The slot under the mouse, which the shift+right-click trigger opens. */
@Mixin(AbstractContainerScreen.class)
public interface HoveredSlotAccessor {

    @Accessor("hoveredSlot")
    Slot shulkersininventory$hoveredSlot();
}
