package com.trevorschoeny.openanywhere.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.Lighting;
import com.trevorschoeny.openanywhere.ItemTurn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** An item drawn with its own light ({@link ItemTurn}) is lit by it in the GUI's item atlas. */
@Mixin(GuiItemAtlas.class)
abstract class ItemLightMixin {

    @WrapOperation(method = "drawToSlot", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Lighting;setupFor(Lcom/mojang/blaze3d/platform/Lighting$Entry;)V"))
    private void openanywhere$light(Lighting lighting, Lighting.Entry entry, Operation<Void> original,
                                    @Local(argsOnly = true) ItemStackRenderState item) {
        if (!ItemTurn.light(item)) original.call(lighting, entry);
    }
}
