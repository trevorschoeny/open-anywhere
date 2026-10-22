package com.trevorschoeny.openanywhere.mixin;

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
    Slot openanywhere$hoveredSlot();

    @Accessor("leftPos")
    int openanywhere$leftPos();

    @Accessor("topPos")
    int openanywhere$topPos();

    @Accessor("imageWidth")
    int openanywhere$imageWidth();
}
