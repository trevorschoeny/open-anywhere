package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.trevlar.menukit.api.panel.ModeScope;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * The slot groups items open in, one per lane: {@link Openable#lanes} for each kind, each
 * a SlotKit group on every menu with no live slots until the server opens an item in it
 * ({@link #open}). From the inventory a shulker box and the ender chest open vanilla's
 * own menus instead ({@link ServerSessions}). What shows the groups is the client's
 * ({@code ClientPanels}).
 *
 * <p>Also here, because both sides need them: the slot lock on opened items, the bundle's
 * per-slot rules, and where a shift-click goes (design, Rule 6).
 */
public final class Groups {

    private Groups() {}

    /** Every group's id within its owner: {@code openanywhere:shulker_0:slots}, and so on. */
    public static final String GROUP = "slots";

    private static final Map<Openable, SlotGroupCategory> CATEGORY = new EnumMap<>(Map.of(
            Openable.SHULKER, new SlotGroupCategory(OpenAnywhere.MOD_ID, "shulker"),
            Openable.BUNDLE, new SlotGroupCategory(OpenAnywhere.MOD_ID, "bundle"),
            Openable.ENDER_CHEST, new SlotGroupCategory(OpenAnywhere.MOD_ID, "ender_chest")));

    private static final Map<Openable, ParityGroup[]> GROUPS = new EnumMap<>(Openable.class);

    /** The group for {@code kind}'s lane {@code lane}. */
    public static ParityGroup group(Openable kind, int lane) {
        return GROUPS.get(kind)[lane];
    }

    /** The lane of {@code kind} whose group {@code slot} belongs to, or -1 for none. */
    public static int laneOf(Slot slot, Openable kind) {
        CreatedSlot created = CreatedSlot.of(slot);
        if (created == null) return -1;
        for (int lane = 0; lane < kind.lanes; lane++) if (created.panelId().equals(kind.slotPanelId(lane))) return lane;
        return -1;
    }

    public static boolean isIn(Slot slot, Openable kind) {
        return laneOf(slot, kind) >= 0;
    }

    /** True for a slot of an opened box or bundle: this mod's own slots, showing an item's contents. */
    static boolean isOpenedContents(Slot slot) {
        return isIn(slot, Openable.SHULKER) || isIn(slot, Openable.BUNDLE);
    }

    static void register() {
        for (Openable kind : Openable.values()) {
            ParityGroup[] lanes = new ParityGroup[kind.lanes];
            for (int lane = 0; lane < kind.lanes; lane++) {
                int l = lane;
                lanes[lane] = ParityGroup.register(kind.panelId(lane), SlotSpec.at(GROUP, CATEGORY.get(kind))
                        .count(kind.slots)
                        .storage(player -> player.level().isClientSide() ? EphemeralStorage.of(kind.slots) : serverStorage(player, kind, l))
                        .shiftClickIn(true)
                        .activeCount(0));
            }
            GROUPS.put(kind, lanes);
        }

        for (int lane = 0; lane < Openable.BUNDLE.lanes; lane++) bundleRules(lane);
        // A box refuses what vanilla's shulker box slot refuses: other boxes, anything that
        // can't go in a container item.
        for (int lane = 0; lane < Openable.SHULKER.lanes; lane++) {
            for (int i = 0; i < Openable.SHULKER.slots; i++) {
                Window.slot(ModeScope.BOTH, Address.createdSlot(group(Openable.SHULKER, lane).id(), i)).gate(new SlotGate() {
                    @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                        return stack.getItem().canFitInsideContainerItems();
                    }
                    @Override public boolean mayPickup(Player player, GatingContext ctx) {
                        return true;
                    }
                });
            }
        }

        // The opened item's own slot refuses every operation while it's open, from
        // vanilla's clicks, keys and drops to any other mod's (SlotKit's slot lock).
        // Hoppers and dispensers don't ask; the per-tick check covers those. Both modes:
        // opening is survival only, but a session open when the player turns creative
        // stays locked until it closes.
        SlotOperations.veto(ModeScope.BOTH, (ref, operation) -> Sessions.isOpenAt(ref.container(), ref.containerSlot()));

        QuickMoveRoutes.register(Groups::route);
    }

    private static Storage serverStorage(Player player, Openable kind, int lane) {
        return switch (kind) {
            case SHULKER -> StackedShulkerStorage.of(player, lane);
            case BUNDLE -> BundleStorage.of(player, lane);
            case ENDER_CHEST -> new EnderChestStorage(player);
        };
    }

    /** How many of a lane's slots are live with its item open. */
    static int liveCount(Player player, Openable kind, int lane) {
        if (kind != Openable.BUNDLE) return kind.slots;
        BundleContents contents = BundleView.contents(player, lane);
        if (contents == null) return 0;
        // Its stacks, plus the add slot unless it's full.
        return BundleView.addSlot(contents) >= 0 ? contents.size() + 1 : contents.size();
    }

    /** Opens a lane's slots for this player, a bundle's sized to the bundle. */
    static void open(ServerPlayer player, Openable kind, int lane) {
        group(kind, lane).setActiveCount(player, liveCount(player, kind, lane));
    }

    static void close(ServerPlayer player, Openable kind, int lane) {
        group(kind, lane).setActiveCount(player, 0);
    }

    /**
     * Each slot's rule in one bundle lane, set by its address, following a bundle in hand:
     * <ul>
     *   <li>The add slot takes as much as vanilla's 64-weight rule allows.</li>
     *   <li>A filled slot never grows: more of the same goes in through the add slot.</li>
     *   <li>A filled slot swaps with a different item on the cursor, when that item fits
     *       the bundle once the slot's stack is out (Trev, 2026-10-16).</li>
     * </ul>
     */
    private static void bundleRules(int lane) {
        for (int i = 0; i < Openable.BUNDLE.slots; i++) {
            int index = i;
            Window.slot(ModeScope.BOTH, Address.createdSlot(group(Openable.BUNDLE, lane).id(), index)).gate(new SlotGate() {
                @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                    BundleContents contents = BundleView.contents(ctx.actingPlayer(), lane);
                    if (contents == null) return false;
                    if (index == BundleView.addSlot(contents)) return BundleView.fit(contents, stack) > 0;
                    if (index >= contents.size() || BundleView.sameItem(contents, index, stack)) return false;
                    return BundleView.fitWithout(contents, index, stack) >= stack.getCount();
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return true;
                }
                @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                    BundleContents contents = BundleView.contents(GatingContext.current().actingPlayer(), lane);
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
     * Rule 6 for what this mod shows in its own slots: a shift-click goes into the opened
     * items, and out of one back to what holds it; what doesn't fit goes where vanilla sends
     * it (Trev, 2026-10-16). With several open, into the newest box or bundle first, then
     * the older ones, then the ender chest. Out of the ender chest, into the open boxes and
     * bundles first, then back to what holds it. A box opened from the inventory, and the
     * ender chest opened the same way, get this from vanilla's own menus.
     */
    private static @Nullable List<Dest> route(AbstractContainerMenu menu, Player player, Slot source) {
        List<Session> open = new ArrayList<>();
        Session ender = null;
        for (Session s : Sessions.of(player)) {
            if (!s.stacked() || s.menu() != menu) continue;
            if (s.kind() == Openable.ENDER_CHEST) ender = s;
            else open.add(s);
        }
        if (open.isEmpty() && ender == null) return null;

        // Out of a box or bundle: back to what holds it.
        for (Session s : open) if (laneOf(source, s.kind()) == s.lane()) return back(s);

        List<Dest> to = new ArrayList<>();
        for (int i = open.size() - 1; i >= 0; i--) to.add(Dest.group(group(open.get(i).kind(), open.get(i).lane()).id()));
        if (ender != null && isIn(source, Openable.ENDER_CHEST)) {
            to.addAll(back(ender));
            return to;
        }
        if (ender != null) to.add(Dest.group(group(Openable.ENDER_CHEST, 0).id()));
        to.add(Dest.VANILLA);
        return to;
    }

    /**
     * Where an opened item's stacks go on shift-click: the inventory, or the container
     * holding it and then the inventory, as a real container screen sends them.
     */
    private static List<Dest> back(Session session) {
        return session.container() instanceof Inventory
                ? List.of(Dest.PLAYER) : List.of(Dest.groupOf(session.menuSlot()), Dest.PLAYER);
    }
}
