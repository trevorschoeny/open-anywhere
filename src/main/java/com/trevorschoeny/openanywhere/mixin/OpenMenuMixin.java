package com.trevorschoeny.openanywhere.mixin;

import java.util.OptionalInt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.trevorschoeny.openanywhere.ScreenBundle;
import com.trevorschoeny.openanywhere.ServerTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;

/**
 * A screen the player opens in the world (a chest, a barrel, a furnace) gets its tab row,
 * and its tab's icon, in the same bundle as the screen itself ({@link ScreenBundle}), so its
 * first frame already shows its tab in front, rather than a frame of the row as it was and
 * then a switch.
 */
@Mixin(ServerPlayer.class)
abstract class OpenMenuMixin {

    @WrapMethod(method = "openMenu")
    private OptionalInt openanywhere$bundle(MenuProvider provider, Operation<OptionalInt> open) {
        return ScreenBundle.around((ServerPlayer) (Object) this, () -> open.call(provider));
    }

    @WrapMethod(method = "openHorseInventory")
    private void openanywhere$bundleMount(AbstractHorse mount, Container inventory, Operation<Void> open) {
        ScreenBundle.around((ServerPlayer) (Object) this, () -> open.call(mount, inventory));
    }

    @WrapMethod(method = "openNautilusInventory")
    private void openanywhere$bundleNautilus(AbstractNautilus mount, Container inventory, Operation<Void> open) {
        ScreenBundle.around((ServerPlayer) (Object) this, () -> open.call(mount, inventory));
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void openanywhere$rowWithScreen(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) ServerTabs.opened((ServerPlayer) (Object) this, provider);
    }

    // A mount's inventory opens without openMenu: a horse's, a llama's, a nautilus's.
    @Inject(method = "openHorseInventory", at = @At("RETURN"))
    private void openanywhere$rowWithMount(AbstractHorse mount, Container inventory, CallbackInfo ci) {
        ServerTabs.openedMount((ServerPlayer) (Object) this, mount);
    }

    @Inject(method = "openNautilusInventory", at = @At("RETURN"))
    private void openanywhere$rowWithNautilus(AbstractNautilus mount, Container inventory, CallbackInfo ci) {
        ServerTabs.openedMount((ServerPlayer) (Object) this, mount);
    }
}
