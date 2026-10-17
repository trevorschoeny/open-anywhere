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

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * The panels the opened items show in, one {@link ContainerPanel} per kind on every
 * menu, hidden and inert until the server opens an item of that kind. A panel's backing
 * is always the same kind; only the opened item behind it changes.
 *
 * <p>Stopgap layout (Trev, 2026-10-16: the real core now, the column when the Kits
 * land): the shulker box and bundle sit above the screen's frame, the ender chest to
 * its left (two panels stacked above the frame overlapped: the stack is laid out once,
 * before either shows). 9 slots to a row. MenuKit's column (K7 to K9) replaces this
 * placement and nothing else.
 *
 * <p>Until SlotKit's server-side reveal (K2) exists, the server treats the hidden slots
 * as live. The gates refuse them without an open session, on both sides, so a client
 * can't put anything into a hidden panel, and the storage with nothing open holds
 * nothing either. Fine for testing, and why this isn't a release build yet.
 */
public final class Panels {

    private Panels() {}

    /** Space between a panel's edge and its slots. */
    public static final int PADDING = 7;

    /** A grid of 9 vanilla slots (18 pixels each): the width the panels wrap at. */
    public static final int ROW_WIDTH = 9 * 18;

    static final SlotGroupCategory ENDER_CHEST_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "ender_chest");
    static final SlotGroupCategory BUNDLE_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "bundle");

    /** The shulker and ender chest panels' one slot group. */
    static final String GROUP = "slots";

    static void register() {
        // Order matters: shift-click from the inventory tries the groups in the order
        // they're registered, so with the ender chest and an item both open it fills
        // the item first (design, Rule 6).
        gridPanel(Openable.SHULKER, SlotGroupCategory.SHULKER_STORAGE,
                PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10), ShulkerStorage::new,
                // As vanilla's ShulkerBoxSlot: no shulker boxes, nothing that can't go inside one.
                stack -> Openable.of(stack) != Openable.SHULKER && stack.getItem().canFitInsideContainerItems());
        bundlePanel();
        gridPanel(Openable.ENDER_CHEST, ENDER_CHEST_STORAGE,
                PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP), EnderStorage::new, stack -> true);

        // The opened item's own slot refuses every operation while it's open, from
        // vanilla's clicks, keys and drops to any other mod's (SlotKit's slot lock).
        // Hoppers and dispensers don't ask; the per-tick check covers those.
        SlotOperations.veto((ref, operation) -> Sessions.isOpenAt(ref.container(), ref.containerSlot()));
    }

    private static ContainerPanel.Builder panel(Openable kind, PanelPosition position) {
        return ContainerPanel.define(kind.panelId())
                .at(position, PADDING)
                .style(PanelStyle.RAISED)
                .pinnedWidth(ROW_WIDTH)
                .visibleWhen(() -> Sessions.CLIENT_SHOWN.contains(kind));
    }

    /** A shulker box or the ender chest: one group of 27. */
    private static void gridPanel(Openable kind, SlotGroupCategory category, PanelPosition position,
                                  Function<Player, Storage> serverStorage, Predicate<ItemStack> accepts) {
        panel(kind, position)
                .addSlot(SlotSpec.at(GROUP, category).count(kind.slots)
                        // The server's storage is the opened item. The client's is a plain
                        // mirror that vanilla's slot sync fills, as for any synced slot.
                        .storage(player -> player.level().isClientSide()
                                ? EphemeralStorage.of(kind.slots) : serverStorage.apply(player))
                        .revealWhen(() -> Sessions.CLIENT_SHOWN.contains(kind))
                        .shiftClickIn(true)
                        .gate(new SlotGate() {
                            @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                                Player player = ctx.actingPlayer();
                                return player != null && Sessions.get(player, kind) != null && accepts.test(stack);
                            }
                            @Override public boolean mayPickup(Player player, GatingContext ctx) {
                                return Sessions.get(player, kind) != null;
                            }
                        }))
                .register();
    }

    /**
     * The bundle: one single-slot group per stack it can hold, each shown on its own, so
     * the panel holds the bundle's stacks plus one slot to add to (the plus), and grows
     * as things go in (Trev, 2026-10-16). When SlotKit's per-group active count (K2)
     * lands, this can become one group again.
     *
     * <p>Each slot's gate follows vanilla's bundle rules: a filled slot can be taken
     * from and never grows (more of the same goes in through the add slot, as with a
     * bundle in hand); the add slot takes as much as the 64-weight rule allows.
     */
    private static void bundlePanel() {
        ContainerPanel.Builder builder = panel(Openable.BUNDLE, PanelPosition.region(OutsideRegion.TOP_CENTER).priority(10));
        for (int i = 0; i < Openable.BUNDLE.slots; i++) {
            int index = i;
            BooleanSupplier shown = () -> Sessions.CLIENT_SHOWN.contains(Openable.BUNDLE)
                    && BundleView.shows(BundleView.contents(Sessions.clientPlayer()), index);
            builder.addSlot(SlotSpec.at(bundleGroup(index), BUNDLE_STORAGE)
                    .storage(player -> player.level().isClientSide()
                            ? EphemeralStorage.of(1) : BundleStorage.of(player).slot(index))
                    .revealWhen(shown)
                    .shiftClickIn(true)
                    .gate(new SlotGate() {
                        @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                            BundleContents contents = BundleView.contents(ctx.actingPlayer());
                            return contents != null && index == BundleView.addSlot(contents) && BundleView.fit(contents, stack) > 0;
                        }
                        @Override public boolean mayPickup(Player player, GatingContext ctx) {
                            return BundleView.contents(player) != null;
                        }
                        @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                            BundleContents contents = BundleView.contents(GatingContext.current().actingPlayer());
                            if (contents == null) return vanillaMax;
                            // A filled slot stays as it is; the add slot takes what fits.
                            if (index < contents.size()) return Math.min(vanillaMax, stack.getCount());
                            return Math.min(vanillaMax, BundleView.fit(contents, stack));
                        }
                    }));
        }
        builder.register();
    }

    /** Bundle slot {@code index}'s group id: {@code s0} to {@code s64}. */
    public static String bundleGroup(int index) {
        return "s" + index;
    }
}
