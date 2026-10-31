package com.trevorschoeny.openanywhere.mixin;

import com.mojang.serialization.MapCodec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/** Vanilla's special item model types, where the villager heads' are added ({@code HeadModels}). Fabric has no registry for them. */
@Mixin(SpecialModelRenderers.class)
public interface SpecialRenderersAccessor {

    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> openanywhere$types() {
        throw new AssertionError();
    }
}
