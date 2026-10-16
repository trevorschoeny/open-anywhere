package com.trevorschoeny.shulkersininventory.mixin;

import com.trevorschoeny.shulkersininventory.ContainerMouseActions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/**
 * Adds the tooltip view's scroll action next to vanilla's bundle one. {@code init}
 * clears the list and re-adds vanilla's on every open and resize, so ours goes in at its
 * tail each time. The creative inventory is left out (design, Rule 4).
 */
@Mixin(AbstractContainerScreen.class)
abstract class AddMouseActionsMixin {

    @Shadow
    protected abstract void addItemSlotMouseAction(ItemSlotMouseAction action);

    @Inject(method = "init", at = @At("TAIL"))
    private void shulkersininventory$addMouseActions(CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen) return;
        addItemSlotMouseAction(new ContainerMouseActions());
    }
}
