package com.trevorschoeny.shulkersininventory;

import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.ContainerPanel;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.SlotOperations;

import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The panels the opened items show in: one SlotKit group per kind (agreed with SlotKit,
 * 2026-10-16), each a {@link ContainerPanel} on every menu, hidden and inert until the
 * server opens an item of that kind. A group's backing is always the same kind; only
 * the opened item behind it changes.
 *
 * <p>Stopgap layout (Trev, 2026-10-16: the real core now, the column when the Kits
 * land): the panels sit above the screen's frame, the ender chest above the other, 9
 * slots to a row. MenuKit's column (K7 to K9) replaces this placement and nothing else.
 *
 * <p>Until SlotKit's server-side reveal (K2) exists, the server treats the hidden slots
 * as live. The gates below refuse them without an open session, on both sides, so a
 * client can't put anything into a hidden panel; the storage with nothing open holds
 * nothing either. Fine for testing, and why this isn't a release build yet.
 */
final class Panels {

    private Panels() {}

    /** A grid of 9 vanilla slots (18 pixels each): the width the panels wrap at. */
    private static final int ROW_WIDTH = 9 * 18;

    static final SlotGroupCategory ENDER_CHEST_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "ender_chest");
    static final SlotGroupCategory BUNDLE_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "bundle");

    /** Each panel's one slot group. */
    static final String GROUP = "slots";

    static void register() {
        panel(Openable.SHULKER, SlotGroupCategory.SHULKER_STORAGE, 10, ShulkerStorage::new,
                // As vanilla's ShulkerBoxSlot: no shulker boxes, nothing that can't go inside one.
                stack -> Openable.of(stack) != Openable.SHULKER && stack.getItem().canFitInsideContainerItems());
        // Bundles are gated slot by slot (BundleRules), so the group gate only needs the session.
        panel(Openable.BUNDLE, BUNDLE_STORAGE, 10, BundleStorage::new, stack -> true);
        BundleRules.register();
        // Registered last, so with the ender chest and a shulker both open, shift-click
        // from the inventory fills the shulker first (design, Rule 6).
        panel(Openable.ENDER_CHEST, ENDER_CHEST_STORAGE, 20, EnderStorage::new, stack -> true);

        // The opened item's own slot refuses every operation while it's open, from
        // vanilla's clicks, keys and drops to any other mod's (SlotKit's slot lock).
        // Hoppers and dispensers don't ask; the per-tick check covers those.
        SlotOperations.veto((ref, operation) -> Sessions.isOpenAt(ref.container(), ref.containerSlot()));
    }

    /**
     * @param priority stacking order above the frame, lower nearer it: the item at 10
     *                 sits right above the screen, the ender chest at 20 above that
     */
    private static void panel(Openable kind, SlotGroupCategory category, int priority,
                              Function<Player, Storage> serverStorage, Predicate<ItemStack> accepts) {
        ContainerPanel.define(kind.panelId())
                .at(PanelPosition.region(OutsideRegion.TOP_CENTER).priority(priority), 7)
                .style(PanelStyle.RAISED)
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> Sessions.CLIENT_SHOWN.contains(kind))
                .addSlot(SlotSpec.at(GROUP, category).count(kind.slots)
                        // The server's storage is the opened item. The client's is a plain
                        // mirror that vanilla's slot sync fills, as for any synced slot.
                        .storage(player -> player.level().isClientSide()
                                ? EphemeralStorage.of(kind.slots) : serverStorage.apply(player))
                        .revealWhen(() -> Sessions.CLIENT_SHOWN.contains(kind))
                        // Shift-click from the inventory screen lands in the opened item
                        // first, the rest where vanilla sends it (Rule 6). On chests this
                        // waits for SlotKit's routes (K3).
                        .shiftClickIn(true)
                        .gate(new SlotGate() {
                            @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                                return isOpen(ctx, kind) && accepts.test(stack);
                            }
                            @Override public boolean mayPickup(Player player, GatingContext ctx) {
                                return Sessions.get(player, kind) != null;
                            }
                        }))
                .register();
    }

    private static boolean isOpen(GatingContext ctx, Openable kind) {
        Player player = ctx.actingPlayer();
        return player != null && Sessions.get(player, kind) != null;
    }
}
