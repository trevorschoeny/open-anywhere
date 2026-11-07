package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.TabWheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

/**
 * The wheel on a container screen is the tabs' first ({@link TabWheel}): ahead of MenuKit's
 * own wheel at the same point, which hands a turn over a panel (the tab row's too) to that
 * panel, and of the screen. A turn the tabs take goes no further.
 */
@Mixin(value = MouseHandler.class, priority = 500)
abstract class TabWheelMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void openanywhere$tabsFirst(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (TabWheel.turn(yOffset)) ci.cancel();
    }
}
