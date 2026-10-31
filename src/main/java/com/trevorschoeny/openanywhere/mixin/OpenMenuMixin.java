package com.trevorschoeny.openanywhere.mixin;

import java.util.OptionalInt;

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
 * and its tab's icon, in the same breath as the screen itself, so its first frame already
 * shows its tab in front, rather than a frame of the row as it was and then a switch.
 */
@Mixin(ServerPlayer.class)
abstract class OpenMenuMixin {

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
