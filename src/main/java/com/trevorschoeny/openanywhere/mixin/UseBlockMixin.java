package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.Opener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block a player clicks, noted while the click runs: a screen it opens (a crafting table,
 * a double chest, a named box) shows that block on its tab.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class UseBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void openanywhere$using(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                    BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Opener.using(new ItemStack(level.getBlockState(hit.getBlockPos()).getBlock()));
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void openanywhere$used(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                   BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Opener.using(ItemStack.EMPTY);
    }
}
