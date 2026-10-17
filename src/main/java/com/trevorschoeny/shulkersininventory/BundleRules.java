package com.trevorschoeny.shulkersininventory;

import com.trevlar.slotkit.api.slot.ContainerPanel;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.Window;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * What the bundle panel's slots accept, by vanilla's bundle rules, one gate per slot
 * (a gate isn't told its slot, but a slot's address is).
 *
 * <ul>
 *   <li>A slot holding a stack can be taken from, and never grows: more of the same
 *       goes in through the empty slot, as with a bundle in hand.</li>
 *   <li>Only the first empty slot takes items, and only as many as the bundle's
 *       64-weight rule allows. The answer is vanilla's own: a dry-run
 *       {@code tryInsert} on a copy of the bundle's contents.</li>
 * </ul>
 *
 * Both sides answer from the bundle in the source slot, so the client predicts what
 * the server will allow.
 */
final class BundleRules {

    private BundleRules() {}

    static void register() {
        var group = ContainerPanel.groupId(Openable.BUNDLE.panelId(), Panels.GROUP);
        for (int i = 0; i < Openable.BUNDLE.slots; i++) {
            int index = i;
            Window.slot(Address.createdSlot(group, index)).gate(new SlotGate() {
                @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                    BundleContents contents = contents(ctx.actingPlayer());
                    return contents != null && index == contents.size() && fit(contents, stack) > 0;
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return contents(player) != null;
                }
                @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                    BundleContents contents = contents(GatingContext.current().actingPlayer());
                    if (contents == null) return vanillaMax;
                    // A filled slot stays as it is; the empty one takes what fits.
                    if (index < contents.size()) return Math.min(vanillaMax, stack.getCount());
                    return Math.min(vanillaMax, fit(contents, stack));
                }
            });
        }
    }

    /** The open bundle's contents as they are now, or null with no bundle open. */
    private static BundleContents contents(Player player) {
        if (player == null) return null;
        Session session = Sessions.get(player, Openable.BUNDLE);
        if (session == null) return null;
        ItemStack bundle = session.container().getItem(session.containerSlot());
        return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    /** How many of {@code stack} the bundle would take, by vanilla's rule. */
    private static int fit(BundleContents contents, ItemStack stack) {
        return contents.asMutable().tryInsert(stack.copy());
    }
}
