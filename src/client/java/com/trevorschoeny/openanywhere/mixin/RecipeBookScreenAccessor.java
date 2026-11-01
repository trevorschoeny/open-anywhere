package com.trevorschoeny.openanywhere.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A book screen's own recipe book, carried to the next tab ({@code RecipeBookCarry}). */
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {

    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> openanywhere$book();
}
