package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.ScreenBundle;

import io.netty.channel.ChannelFutureListener;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/** While a screen opens, what the server sends the player waits for the screen's bundle ({@link ScreenBundle}). */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class HoldForBundleMixin {

    @Shadow @Final protected MinecraftServer server;

    // A packet sent with a listener still goes straight out, ahead of the held ones. Nothing
    // sends one while a screen opens; hold it too, with its listener, if something starts to.
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void openanywhere$hold(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        if (listener == null && server.isSameThread() && ScreenBundle.hold((ServerCommonPacketListenerImpl) (Object) this, packet)) ci.cancel();
    }
}
