package com.trevorschoeny.shulkersininventory;

import java.util.List;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes.Dest;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.Window;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * The bundle's slots, and a shulker box's when it opens on a chest's screen, each a
 * SlotKit group on every menu, with no live slots until the server opens one
 * ({@link #open}). From the inventory a shulker box and the ender chest open vanilla's
 * own menus instead ({@link ServerSessions}). What shows the groups is the client's
 * ({@code ClientPanels}).
 *
 * <p>Also here, because both sides need them: the slot lock on the opened item, the
 * bundle's per-slot rules, and where a shift-click goes (design, Rule 6).
 */
public final class Groups {

    private Groups() {}

    /** Every group's id within its owner: {@code shulkersininventory:shulker:slots}, and so on. */
    public static final String GROUP = "slots";

    static final SlotGroupCategory BUNDLE_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "bundle");

    static final SlotGroupCategory SHULKER_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "shulker");

    private static ParityGroup bundle;
    private static ParityGroup shulker;

    /** The bundle's group. */
    public static ParityGroup bundle() {
        return bundle;
    }

    /**
     * The group for a shulker box opened on a chest's screen, shown as a section below the
     * chest. From the inventory a box opens vanilla's own screen instead.
     */
    public static ParityGroup shulker() {
        return shulker;
    }

    public static ParityGroup of(Openable kind) {
        return kind == Openable.BUNDLE ? bundle : shulker;
    }

    static void register() {
        bundle = ParityGroup.register(Openable.BUNDLE.panelId(), SlotSpec.at(GROUP, BUNDLE_STORAGE)
                .count(Openable.BUNDLE.slots)
                .storage(player -> player.level().isClientSide()
                        ? EphemeralStorage.of(Openable.BUNDLE.slots) : BundleStorage.of(player))
                .shiftClickIn(true)
                .activeCount(0));
        bundleRules();

        shulker = ParityGroup.register(Openable.SHULKER.panelId(), SlotSpec.at(GROUP, SHULKER_STORAGE)
                .count(Openable.SHULKER.slots)
                .storage(player -> player.level().isClientSide()
                        ? EphemeralStorage.of(Openable.SHULKER.slots) : StackedShulkerStorage.of(player))
                .shiftClickIn(true)
                .activeCount(0));
        // A box refuses what vanilla's shulker box slot refuses: other boxes, anything that
        // can't go in a container item.
        for (int i = 0; i < Openable.SHULKER.slots; i++) {
            Window.slot(ModeScope.BOTH, Address.createdSlot(shulker.id(), i)).gate(new SlotGate() {
                @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                    return stack.getItem().canFitInsideContainerItems();
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return true;
                }
            });
        }

        // The opened item's own slot refuses every operation while it's open, from
        // vanilla's clicks, keys and drops to any other mod's (SlotKit's slot lock).
        // Hoppers and dispensers don't ask; the per-tick check covers those. Both modes:
        // opening is survival only, but a session open when the player turns creative
        // stays locked until it closes.
        SlotOperations.veto(ModeScope.BOTH, (ref, operation) -> Sessions.isOpenAt(ref.container(), ref.containerSlot()));

        QuickMoveRoutes.register(Groups::route);
    }

    /** How many of a kind's slots are live with it open. */
    static int liveCount(Player player, Openable kind) {
        if (kind == Openable.SHULKER) return Openable.SHULKER.slots;
        BundleContents contents = BundleView.contents(player);
        if (contents == null) return 0;
        // Its stacks, plus the add slot unless it's full.
        return BundleView.addSlot(contents) >= 0 ? contents.size() + 1 : contents.size();
    }

    /** Opens a kind's slots for this player, the bundle's sized to the open bundle. */
    static void open(ServerPlayer player, Openable kind) {
        of(kind).setActiveCount(player, liveCount(player, kind));
    }

    static void close(ServerPlayer player, Openable kind) {
        of(kind).setActiveCount(player, 0);
    }

    /**
     * Each bundle slot's rule, set by its address, following a bundle in hand:
     * <ul>
     *   <li>The add slot takes as much as vanilla's 64-weight rule allows.</li>
     *   <li>A filled slot never grows: more of the same goes in through the add slot.</li>
     *   <li>A filled slot swaps with a different item on the cursor, when that item fits
     *       the bundle once the slot's stack is out (Trev, 2026-10-16).</li>
     * </ul>
     */
    private static void bundleRules() {
        for (int i = 0; i < Openable.BUNDLE.slots; i++) {
            int index = i;
            Window.slot(ModeScope.BOTH, Address.createdSlot(bundle.id(), index)).gate(new SlotGate() {
                @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                    BundleContents contents = BundleView.contents(ctx.actingPlayer());
                    if (contents == null) return false;
                    if (index == BundleView.addSlot(contents)) return BundleView.fit(contents, stack) > 0;
                    if (index >= contents.size() || BundleView.sameItem(contents, index, stack)) return false;
                    return BundleView.fitWithout(contents, index, stack) >= stack.getCount();
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return true;
                }
                @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                    BundleContents contents = BundleView.contents(GatingContext.current().actingPlayer());
                    if (contents == null) return vanillaMax;
                    if (index >= contents.size()) return Math.min(vanillaMax, BundleView.fit(contents, stack));
                    // Asked about the slot's own stack (a merge): it stays as it is.
                    if (BundleView.sameItem(contents, index, stack)) return Math.min(vanillaMax, stack.getCount());
                    // Asked about a different stack (a swap): what fits in its place.
                    return Math.min(vanillaMax, BundleView.fitWithout(contents, index, stack));
                }
            });
        }
    }

    /**
     * Rule 6 for what this mod shows in its own slots, the bundle and a box below a chest:
     * a shift-click goes into it, and out of it back to what holds it; what doesn't fit
     * goes where vanilla sends it (Trev, 2026-10-16). A box opened from the inventory, and
     * the ender chest, get this from vanilla's own menus.
     */
    private static @Nullable List<Dest> route(net.minecraft.world.inventory.AbstractContainerMenu menu, Player player, Slot source) {
        for (Openable kind : new Openable[] {Openable.SHULKER, Openable.BUNDLE}) {
            Session open = Sessions.get(player, kind);
            if (open == null || !open.stacked() || open.menu() != menu) continue;
            // Out of it: back to what holds it. From anywhere else: into it.
            if (isIn(source, kind)) return back(open);
            return List.of(Dest.group(of(kind).id()), Dest.VANILLA);
        }
        return null;
    }

    /**
     * Where an opened item's stacks go on shift-click: the inventory, or the container
     * holding it and then the inventory, as a real container screen sends them.
     */
    private static List<Dest> back(Session session) {
        return session.container() instanceof Inventory
                ? List.of(Dest.PLAYER) : List.of(Dest.groupOf(session.menuSlot()), Dest.PLAYER);
    }

    /** True for a slot of an opened box or bundle: this mod's own slots, showing an item's contents. */
    static boolean isOpenedContents(Slot slot) {
        return isIn(slot, Openable.SHULKER) || isIn(slot, Openable.BUNDLE);
    }

    static boolean isIn(Slot slot, Openable kind) {
        CreatedSlot created = CreatedSlot.of(slot);
        return created != null && created.panelId().equals(kind.slotPanelId());
    }
}
