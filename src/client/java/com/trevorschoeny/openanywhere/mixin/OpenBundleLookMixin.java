package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.ClientMarks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * An open bundle is drawn open in its slot, with vanilla's own open-bag art (Trev,
 * 2026-10-22): the stack drawn, not the stack held, so nothing about the item changes.
 */
@Mixin(AbstractContainerScreen.class)
abstract class OpenBundleLookMixin {

    @ModifyVariable(method = "extractSlot", at = @At("STORE"), ordinal = 0)
    private ItemStack openanywhere$openBundle(ItemStack stack, GuiGraphicsExtractor graphics, Slot slot) {
        return ClientMarks.drawnAs(slot, stack);
    }
}
