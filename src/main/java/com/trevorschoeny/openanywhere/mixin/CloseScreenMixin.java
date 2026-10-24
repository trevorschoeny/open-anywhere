package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.ServerTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The player closed the screens themselves (Escape, the inventory key): what they carried
 * goes back or drops, so a box lifted off its tab doesn't reopen as it lands. A tab switch
 * sends no close, so a lifted box still rides along to the next tab.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class CloseScreenMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void openanywhere$closed(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        ServerTabs.closed(player);
    }
}
