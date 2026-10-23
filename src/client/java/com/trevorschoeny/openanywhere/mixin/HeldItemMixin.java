package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.DoubleClick;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The item just picked up by what may be the first half of a double-click is drawn where
 * it sat, not at the cursor, until the window passes or the cursor moves (Trev,
 * 2026-10-22): a double-click that opens it then shows no movement at all.
 */
@Mixin(AbstractContainerScreen.class)
abstract class HeldItemMixin {

    @ModifyArgs(method = "extractCarriedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractFloatingItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void openanywhere$held(Args args, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] at = DoubleClick.heldAt(mouseX, mouseY);
        if (at == null) return;
        args.set(2, at[0]);
        args.set(3, at[1]);
    }
}
