package com.trevorschoeny.openanywhere.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The window height a recipe book was last laid out for, so a borrowed book stays where it was ({@code RecipeBookCarry}). */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookAccessor {

    @Accessor("height")
    int openanywhere$height();
}
