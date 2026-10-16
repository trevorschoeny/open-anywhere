package com.trevorschoeny.shulkersininventory.mixin;

import com.trevorschoeny.shulkersininventory.ClientView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.component.BundleContents;

/**
 * A bundle's tooltip shows, and its scroll reaches, every stack instead of the first 12
 * ("+n" for the rest). The server already accepts any index ({@code indexIsOutsideAllowedBounds}
 * checks only the list size); only this client-side count capped it. Vanilla's tooltip,
 * scroll and take then work unchanged.
 *
 * <p>ponytail: vanilla's grid is 4 wide, so a bundle of many single items is a tall
 * tooltip (64 kinds: 16 rows). Rare; a wider grid if it ever matters.
 */
@Mixin(BundleContents.class)
abstract class ShowAllBundleItemsMixin {

    @Shadow
    public abstract int size();

    @Inject(method = "getNumberOfItemsToShow", at = @At("HEAD"), cancellable = true)
    private void shulkersininventory$showAll(CallbackInfoReturnable<Integer> cir) {
        if (ClientView.allBundleItems()) cir.setReturnValue(size());
    }
}
