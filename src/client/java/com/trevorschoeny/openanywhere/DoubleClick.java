package com.trevorschoeny.openanywhere;

import com.trevlar.slotkit.api.slot.SlotDoubleClick;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;

/**
 * A plain left double-click on an openable item opens it; on an item that's open, it closes
 * it. Shift+double-click stays vanilla's "move all".
 *
 * <p>SlotKit's {@link SlotDoubleClick} owns the gesture: the window, the still cursor, the real
 * mouse, creative's real slot, and putting the lifted item back on the client. This mod says
 * which items qualify and asks the server to open; the server puts the item back too, and its
 * sync corrects the client if it refuses.
 *
 * <p>A stack of boxes (Carpet) never opens, so its double-click stays vanilla's gather; a stack
 * of ender chests opens the ender chest, giving up gather for ender chests.
 */
final class DoubleClick {

    private DoubleClick() {}

    static void register() {
        SlotDoubleClick.register(OpenAnywhere.MOD_ID + ":open",
                (slot, stack) -> ClientPlayNetworking.canSend(OpenC2S.TYPE) // a server without the mod keeps vanilla's gather
                        && Settings.doubleClick()
                        // A click with the Open key held is an open of its own, not half a double-click.
                        && !KeybinderyAPI.getInstance().isHeld(OpenAnywhereClient.OPEN)
                        && opens(stack)
                        && OpenAnywhereClient.opensOrIsOpen(Minecraft.getInstance().player, slot),
                pair -> {
                    // PUT_BACK: the first click lifted the item, and the server puts it back from
                    // the cursor. Creative told the server the slot's contents itself when SlotKit
                    // put it back, so there it opens as if never lifted.
                    // IN_PLACE: the item never left (its slot is locked): an open item, closed.
                    boolean fromCursor = pair.kind() == SlotDoubleClick.Kind.PUT_BACK
                            && !(pair.screen() instanceof CreativeModeInventoryScreen);
                    ClientPlayNetworking.send(new OpenC2S(pair.menuIndex(), fromCursor));
                });
    }

    /** An item a double-click opens: one box or bundle, or any stack of ender chests. */
    private static boolean opens(ItemStack stack) {
        Openable kind = Openable.of(stack);
        return kind == Openable.ENDER_CHEST || kind != null && stack.getCount() == 1;
    }
}
