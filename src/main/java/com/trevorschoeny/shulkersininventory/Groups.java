package com.trevorschoeny.shulkersininventory;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes.Dest;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.storage.Storage;
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
 * The slots the opened items show, as SlotKit groups: one group per kind, on every
 * menu, with no live slots until the server opens an item of that kind
 * ({@link #open}). A group's backing is always the same kind; only the opened item
 * behind it changes. The panels that show them are the client's ({@code ClientPanels}).
 *
 * <p>Also here, because both sides need them: the slot lock on the opened item, the
 * bundle's per-slot rules, and where a shift-click goes (design, Rule 6).
 */
public final class Groups {

    private Groups() {}

    /** Every group's id within its owner: {@code shulkersininventory:shulker:slots}, and so on. */
    public static final String GROUP = "slots";

    static final SlotGroupCategory ENDER_CHEST_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "ender_chest");
    static final SlotGroupCategory BUNDLE_STORAGE = new SlotGroupCategory(ShulkersInInventory.MOD_ID, "bundle");

    private static ParityGroup shulker, bundle, enderChest;

    public static ParityGroup of(Openable kind) {
        return switch (kind) {
            case SHULKER -> shulker;
            case BUNDLE -> bundle;
            case ENDER_CHEST -> enderChest;
        };
    }

    static void register() {
        shulker = grid(Openable.SHULKER, SlotGroupCategory.SHULKER_STORAGE, ShulkerStorage::new,
                // As vanilla's ShulkerBoxSlot: no shulker boxes, nothing that can't go inside one.
                stack -> Openable.of(stack) != Openable.SHULKER && stack.getItem().canFitInsideContainerItems());
        bundle = ParityGroup.register(Openable.BUNDLE.panelId(), SlotSpec.at(GROUP, BUNDLE_STORAGE)
                .count(Openable.BUNDLE.slots)
                .storage(player -> player.level().isClientSide()
                        ? EphemeralStorage.of(Openable.BUNDLE.slots) : BundleStorage.of(player))
                .shiftClickIn(true)
                .activeCount(0));
        bundleRules();
        enderChest = grid(Openable.ENDER_CHEST, ENDER_CHEST_STORAGE, EnderStorage::new, stack -> true);

        // The opened item's own slot refuses every operation while it's open, from
        // vanilla's clicks, keys and drops to any other mod's (SlotKit's slot lock).
        // Hoppers and dispensers don't ask; the per-tick check covers those.
        SlotOperations.veto((ref, operation) -> Sessions.isOpenAt(ref.container(), ref.containerSlot()));

        QuickMoveRoutes.register(Groups::route);
    }

    /** A shulker box or the ender chest: 27 slots, live only while one is open. */
    private static ParityGroup grid(Openable kind, SlotGroupCategory category,
                                    Function<Player, Storage> serverStorage, Predicate<ItemStack> accepts) {
        return ParityGroup.register(kind.panelId(), SlotSpec.at(GROUP, category).count(kind.slots)
                // The server's storage is the opened item. The client's is a plain mirror
                // that vanilla's slot sync fills, as for any synced slot.
                .storage(player -> player.level().isClientSide()
                        ? EphemeralStorage.of(kind.slots) : serverStorage.apply(player))
                .shiftClickIn(true)
                .activeCount(0)
                .accepts(accepts));
    }

    /** How many of a kind's slots are live with this item open. */
    static int liveCount(Openable kind, Player player) {
        if (kind != Openable.BUNDLE) return kind.slots;
        BundleContents contents = BundleView.contents(player);
        if (contents == null) return 0;
        // Its stacks, plus the add slot unless it's full.
        return BundleView.addSlot(contents) >= 0 ? contents.size() + 1 : contents.size();
    }

    /** Opens the kind's slots for this player, sized to the open item. */
    static void open(ServerPlayer player, Openable kind) {
        of(kind).setActiveCount(player, liveCount(kind, player));
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
            Window.slot(Address.createdSlot(bundle.id(), index)).gate(new SlotGate() {
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
     * Rule 6: with an item open, a shift-click goes into it, from the inventory or the
     * open container; out of it, back to what holds it. With only the ender chest open,
     * the ender chest is that item. Whatever doesn't fit goes where vanilla sends it
     * (Trev, 2026-10-16).
     */
    private static @Nullable List<Dest> route(net.minecraft.world.inventory.AbstractContainerMenu menu, Player player, Slot source) {
        Session item = Sessions.get(player, Openable.SHULKER);
        if (item == null) item = Sessions.get(player, Openable.BUNDLE);
        Session target = item != null ? item : Sessions.get(player, Openable.ENDER_CHEST);
        if (target == null) return null;
        // Out of the item: back to what holds it.
        if (isIn(source, target.kind())) return back(target);
        // From the inventory, a chest, or the ender chest beside it: into the item.
        return List.of(Dest.group(of(target.kind()).id()), Dest.VANILLA);
    }

    /**
     * Where an opened item's stacks go on shift-click: the inventory, or the container
     * holding it and then the inventory, as a real container screen sends them.
     */
    private static List<Dest> back(Session session) {
        return session.container() instanceof Inventory
                ? List.of(Dest.PLAYER) : List.of(Dest.groupOf(session.menuSlot()), Dest.PLAYER);
    }

    private static boolean isIn(Slot slot, Openable kind) {
        CreatedSlot created = CreatedSlot.of(slot);
        return created != null && created.panelId().equals(kind.slotPanelId());
    }
}
