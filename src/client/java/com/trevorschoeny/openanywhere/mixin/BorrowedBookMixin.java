package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.RecipeBookCarry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A container screen without a recipe book of its own showing the one carried from the last
 * tab ({@link RecipeBookCarry}), as vanilla's book screens show theirs
 * ({@code AbstractRecipeBookScreen}): the frame beside it, the book drawn above the screen
 * and under the carried item, its clicks and keys first, its typing once focused (a child
 * widget, which vanilla never finds under the mouse: the book says it isn't, so its clicks are
 * passed to it here), its tooltips, its tick, and a click on it not a click outside the frame,
 * which would throw the carried item.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class BorrowedBookMixin extends Screen {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Shadow @Final protected int imageHeight;
    @Shadow protected Slot hoveredSlot;

    // Whether this screen has initialised before: a resize, not a new tab.
    @Unique private boolean openanywhere$inited;

    protected BorrowedBookMixin(Component title) {
        super(title);
    }

    // At the end of the base init, before a subclass places its widgets from leftPos.
    @Inject(method = "init", at = @At("RETURN"))
    private void openanywhere$borrow(CallbackInfo ci) {
        boolean first = !openanywhere$inited;
        openanywhere$inited = true;
        int left = RecipeBookCarry.initialised((AbstractContainerScreen<?>) (Object) this, imageWidth, first);
        if (left < 0) return;
        leftPos = left;
        addWidget(RecipeBookCarry.shownOn(this));
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractCarriedItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void openanywhere$drawBook(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book == null) return;
        g.nextStratum();
        book.extractRenderState(g, mouseX, mouseY, a);
        g.nextStratum();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void openanywhere$bookTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book != null) book.extractTooltip(g, mouseX, mouseY, hoveredSlot);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void openanywhere$clickBook(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book == null || !book.mouseClicked(event, doubleClick)) return;
        setFocused(book);
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void openanywhere$keyBook(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book != null && book.keyPressed(event)) cir.setReturnValue(true);
    }

    @Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
    private void openanywhere$onBook(double mx, double my, int left, int top, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book != null && cir.getReturnValueZ() && !book.hasClickedOutside(mx, my, leftPos, topPos, imageWidth, imageHeight)) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void openanywhere$tickBook(CallbackInfo ci) {
        RecipeBookComponent<?> book = RecipeBookCarry.shownOn(this);
        if (book != null) book.tick();
    }
}
