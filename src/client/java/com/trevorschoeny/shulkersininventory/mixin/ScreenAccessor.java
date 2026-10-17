package com.trevorschoeny.shulkersininventory.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * A container screen's slot under the mouse (what shift+right-click opens) and its
 * frame (where the close button sits on vanilla's own screens).
 */
@Mixin(AbstractContainerScreen.class)
public interface ScreenAccessor {

    @Accessor("hoveredSlot")
    Slot shulkersininventory$hoveredSlot();

    @Accessor("leftPos")
    int shulkersininventory$leftPos();

    @Accessor("topPos")
    int shulkersininventory$topPos();

    @Accessor("imageWidth")
    int shulkersininventory$imageWidth();
}
