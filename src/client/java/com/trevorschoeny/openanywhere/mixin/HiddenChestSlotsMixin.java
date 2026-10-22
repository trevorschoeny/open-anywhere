package com.trevorschoeny.openanywhere.mixin;

import com.trevorschoeny.openanywhere.LayoutPrototype;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.Slot;

/**
 * Layout prototype, "Tabs": while another tab is picked, the chest's own slots are
 * inactive on the client, so vanilla neither draws nor hit-tests them. Hiding the chest's
 * section alone hands its slots back to vanilla's place, right over the picked section
 * (SlotKit gives a hidden element's vanilla slots back to the menu's layout).
 * ponytail: the container list would park a hidden section's vanilla slots itself.
 */
@Mixin(Slot.class)
abstract class HiddenChestSlotsMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void openanywhere$hiddenChest(CallbackInfoReturnable<Boolean> cir) {
        if (LayoutPrototype.hidesChestSlot((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
