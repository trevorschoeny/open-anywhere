package com.trevorschoeny.shulkersininventory.mixin;

import java.util.function.Consumer;

import com.trevorschoeny.shulkersininventory.ClientView;
import com.trevorschoeny.shulkersininventory.Openable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Drops vanilla's text list of a shulker box's contents ("Stone x5 ... and 3 more")
 * while the grid shows them. Other items with a container component keep it.
 */
@Mixin(ItemContainerContents.class)
abstract class HideContainerTextMixin {

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void shulkersininventory$hideText(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag,
                                              DataComponentGetter components, CallbackInfo ci) {
        if (components instanceof ItemStack stack && Openable.isShulker(stack) && ClientView.shulkerGrid()) ci.cancel();
    }
}
