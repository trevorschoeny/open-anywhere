package com.trevorschoeny.shulkersininventory.mixin;

import com.trevorschoeny.shulkersininventory.Openable;
import com.trevorschoeny.shulkersininventory.Selection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The tooltip view's take: right-click a shulker box or ender chest with an empty cursor,
 * after scrolling to a stack in its tooltip, and that stack comes out onto the cursor.
 *
 * <p>This is vanilla's own hook for "an item reacts to being clicked", the one a bundle
 * uses for the same gesture ({@code BundleItem.overrideOtherStackedOnMe}). Shulker boxes
 * and ender chests are plain {@code BlockItem}s, which don't override it, so the
 * injection lands in {@code Item}'s default. It runs on both sides: on the client as
 * vanilla's click prediction, on the server for real; both read the same
 * {@link Selection}, and the server's result is what syncs back.
 *
 * <p>Nothing else moves an item out: a click without a selection, with something on
 * the cursor, or on another slot is vanilla's (a right-click then just picks the box up).
 */
@Mixin(Item.class)
abstract class TakeSelectedMixin {

    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void shulkersininventory$takeSelected(ItemStack self, ItemStack carried, Slot slot, ClickAction action,
                                                  Player player, SlotAccess carriedAccess,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY || !carried.isEmpty()) return;
        Selection.Entry selected = Selection.get(player);
        if (selected == null || selected.slot() != slot.index) return;

        ItemStack taken = ItemStack.EMPTY;
        if (Openable.isShulker(self) && self.getCount() == 1) {
            taken = takeFromShulker(self, selected.index());
        } else if (Openable.isEnderChest(self)) {
            taken = takeFromEnderChest(player, selected.index());
        }
        if (taken.isEmpty()) return;

        carriedAccess.set(taken);
        // The selected stack is gone; scrolling picks again. Cleared on this side here,
        // and the other side clears its own copy in the same click.
        Selection.clear(player);
        cir.setReturnValue(true);
    }

    /** Removes stack {@code index} from the box's contents and writes the rest back. */
    private static ItemStack takeFromShulker(ItemStack box, int index) {
        if (index < 0 || index >= Openable.GRID_SIZE) return ItemStack.EMPTY;
        NonNullList<ItemStack> items = NonNullList.withSize(Openable.GRID_SIZE, ItemStack.EMPTY);
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        ItemStack taken = items.get(index);
        if (taken.isEmpty()) return ItemStack.EMPTY;
        items.set(index, ItemStack.EMPTY);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return taken;
    }

    /**
     * Removes stack {@code index} from the player's ender chest. Clicking an ender chest
     * item means one is right there, which is the rule for reaching it (design, Rule 1).
     */
    private static ItemStack takeFromEnderChest(Player player, int index) {
        Container ender = Openable.enderChest(player);
        if (index < 0 || index >= ender.getContainerSize()) return ItemStack.EMPTY;
        ItemStack taken = ender.removeItemNoUpdate(index);
        ender.setChanged();
        return taken;
    }
}
