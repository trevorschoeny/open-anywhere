package com.trevorschoeny.openanywhere.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trevorschoeny.openanywhere.RecipeBookCarry;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The window as a vanilla screen without a book of its own reads it while it shows a borrowed
 * one ({@link RecipeBookCarry}): wide enough that its own centring, {@code (width - imageWidth) / 2},
 * lands on the {@code leftPos} the book moved the frame to. These screens place their image
 * (the chest texture), the anvil's name field, the merchant's offers and the enchanting
 * table's clicks from the window rather than from {@code leftPos}, so without it the image
 * stays where it was, half under the book, while the slots moved. Vanilla's book screens
 * work from {@code leftPos} already and aren't here; nor are the ones that place everything
 * from it (the loom, stonecutter, smithing and cartography tables). As MenuKit reads the
 * height for its centring ({@code MKFrameCentringHeightMixin}), each class's own field.
 */
@Mixin({AbstractMountInventoryScreen.class, AnvilScreen.class, BeaconScreen.class, BrewingStandScreen.class, ContainerScreen.class,
        CrafterScreen.class, DispenserScreen.class, EnchantmentScreen.class, GrindstoneScreen.class, HopperScreen.class,
        MerchantScreen.class, ShulkerBoxScreen.class})
public abstract class BorrowedBookWidthMixin {

    @ModifyExpressionValue(method = "*", require = 0, at = {
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractMountInventoryScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AnvilScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/BeaconScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/BrewingStandScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/ContainerScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/CrafterScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/DispenserScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/EnchantmentScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/GrindstoneScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/HopperScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;width:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/ShulkerBoxScreen;width:I", opcode = Opcodes.GETFIELD)})
    private int openanywhere$besideBook(int width) {
        return RecipeBookCarry.width((AbstractContainerScreen<?>) (Object) this, width);
    }
}
