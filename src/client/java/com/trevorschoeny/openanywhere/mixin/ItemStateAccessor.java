package com.trevorschoeny.openanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** A resolved item's layers, for {@link ItemTurnMixin}. */
@Mixin(ItemStackRenderState.class)
public interface ItemStateAccessor {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] openanywhere$layers();

    @Accessor("activeLayerCount")
    int openanywhere$activeLayerCount();
}
