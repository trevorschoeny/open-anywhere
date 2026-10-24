package com.trevorschoeny.openanywhere.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.Lighting;
import com.trevorschoeny.openanywhere.ItemTurn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;

/** A turned item drawn past its box ({@link ItemTurn}) is lit by its own light too. */
@Mixin(OversizedItemRenderer.class)
abstract class OversizedItemLightMixin {

    @WrapOperation(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/OversizedItemRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Lighting;setupFor(Lcom/mojang/blaze3d/platform/Lighting$Entry;)V"))
    private void openanywhere$light(Lighting lighting, Lighting.Entry entry, Operation<Void> original,
                                    @Local(argsOnly = true) OversizedItemRenderState state) {
        if (!ItemTurn.light(state.guiItemRenderState().itemStackRenderState())) original.call(lighting, entry);
    }
}
