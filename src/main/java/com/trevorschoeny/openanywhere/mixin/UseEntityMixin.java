package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.Heads;
import com.trevorschoeny.openanywhere.Opener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The entity a player interacts with, noted while the interaction runs: a screen it opens
 * shows the entity's icon on its tab ({@link Heads#of}: a villager's head, else its item). Server side only: the
 * client's player runs this too, on its own thread.
 */
@Mixin(Player.class)
abstract class UseEntityMixin {

    @Inject(method = "interactOn", at = @At("HEAD"))
    private void openanywhere$using(Entity entity, InteractionHand hand, Vec3 at, CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object) this instanceof ServerPlayer)) return;
        Opener.using(Heads.of(entity));
    }

    @Inject(method = "interactOn", at = @At("RETURN"))
    private void openanywhere$used(Entity entity, InteractionHand hand, Vec3 at, CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this instanceof ServerPlayer) Opener.using(ItemStack.EMPTY);
    }
}
