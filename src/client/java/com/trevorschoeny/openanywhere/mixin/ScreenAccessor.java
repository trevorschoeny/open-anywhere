package com.trevorschoeny.openanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * A container screen's slot under the mouse (what a double-click or the Open key acts on)
 * and its frame (where a lifted item is drawn back in its slot, and the close button sits).
 */
@Mixin(AbstractContainerScreen.class)
public interface ScreenAccessor {

    @Accessor("hoveredSlot")
    Slot openanywhere$hoveredSlot();

    @Accessor("leftPos")
    int openanywhere$leftPos();

    @Accessor("topPos")
    int openanywhere$topPos();

    @Accessor("imageWidth")
    int openanywhere$imageWidth();

    @Accessor("imageHeight")
    int openanywhere$imageHeight();

}
