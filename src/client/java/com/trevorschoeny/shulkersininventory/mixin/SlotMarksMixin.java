package com.trevorschoeny.shulkersininventory.mixin;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevorschoeny.shulkersininventory.ClientMarks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Marks drawn over slots: the opened item's slot greyed, the way Shulker Palette greys
 * the box it has open (a disabled control's grey), and the bundle's add slot marked
 * with MenuKit's plus.
 */
@Mixin(AbstractContainerScreen.class)
abstract class SlotMarksMixin {

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void shulkersininventory$marks(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ClientMarks.draw(graphics, slot, CreatedSlot.of(slot));
    }
}
