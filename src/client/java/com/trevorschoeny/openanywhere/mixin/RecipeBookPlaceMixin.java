package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.RecipeBookCarry;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A recipe picked on a book borrowed by a screen without one: vanilla would send it for the
 * screen in front, which has no grid, and the server would ignore it. It goes to the book's
 * own tab instead ({@link RecipeBookCarry#placeAtHome}).
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookPlaceMixin {

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void openanywhere$placeAtHome(RecipeCollection collection, RecipeDisplayId recipe, boolean max, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeBookCarry.placeAtHome((RecipeBookComponent<?>) (Object) this, recipe, max)) cir.setReturnValue(true);
    }
}
