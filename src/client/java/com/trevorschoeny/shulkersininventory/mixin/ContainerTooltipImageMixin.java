package com.trevorschoeny.shulkersininventory.mixin;

import java.util.Optional;

import com.trevorschoeny.shulkersininventory.ClientGrids;
import com.trevorschoeny.shulkersininventory.ClientView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Gives shulker boxes and the ender chest a grid in their tooltip. Their item class has
 * no tooltip image of its own (a bundle's does), so the stack's lookup is where it goes.
 */
@Mixin(ItemStack.class)
abstract class ContainerTooltipImageMixin {

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void shulkersininventory$grid(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (ClientView.showsGrid(self)) cir.setReturnValue(Optional.of(ClientGrids.tooltip(self)));
    }
}
