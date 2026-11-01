package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.TabWheel;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla screens whose list takes every turn of the wheel, wherever the mouse is and
 * whether or not it scrolls (the merchant's offers, the stonecutter's recipes, the loom's
 * patterns): a turn the tab wheel would take ({@link TabWheel#takes}) skips their list and
 * goes on as a turn nobody used, after the base screen's own (a bundle's wheel), so the tab
 * wheel sees it as it does on every other screen.
 */
@Mixin({MerchantScreen.class, StonecutterScreen.class, LoomScreen.class})
public abstract class ListWheelMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    private ListWheelMixin(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void openanywhere$tabsFirst(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (TabWheel.takes(this, x, y, scrollY)) cir.setReturnValue(super.mouseScrolled(x, y, scrollX, scrollY));
    }
}
