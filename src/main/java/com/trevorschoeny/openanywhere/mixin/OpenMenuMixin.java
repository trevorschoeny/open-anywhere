package com.trevorschoeny.openanywhere.mixin;

import java.util.OptionalInt;

import com.trevorschoeny.openanywhere.ServerTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/**
 * A screen the player opens in the world (a chest, a barrel) gets its tab row in the same
 * breath as the screen itself, so its first frame already shows the chest's tab in front,
 * rather than a frame of the row as it was and then a switch.
 */
@Mixin(ServerPlayer.class)
abstract class OpenMenuMixin {

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void openanywhere$rowWithScreen(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) ServerTabs.opened((ServerPlayer) (Object) this);
    }
}
