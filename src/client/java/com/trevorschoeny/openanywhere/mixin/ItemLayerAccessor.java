package com.trevorschoeny.openanywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** An item layer's display transform, for {@link ItemTurnMixin}. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerAccessor {

    @Accessor("itemTransform")
    ItemTransform openanywhere$itemTransform();
}
