package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.OnDemandGroup;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes.Dest;
import com.trevlar.slotkit.api.slot.SlotSpec;
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
 * The slot groups items open in: one SlotKit {@link OnDemandGroup} per kind, on no menu
 * until the server opens an item, when an instance of it joins the open menu ({@link #add}),
 * and leaves when the item closes. As many open at once as the player likes (Trev,
 * 2026-10-22). An instance's number on its menu is the session's lane
 * ({@link Session#lane()}): SlotKit gives the lowest free one, the client hears the same.
 * What shows the groups is the client's ({@code ClientPanels}).
 *
 * <p>Also here, because both sides need them: the slot lock on opened items, the bundle's
 * per-slot rules, and where a shift-click goes (design, Rule 6).
 */
public final class Groups {

    private Groups() {}

    /** Every group's id within its owner: {@code openanywhere:shulker_0:slots}, and so on. */
    public static final String GROUP = "slots";

    /**
     * What each kind's slots are to other mods: a box is shulker storage and the ender chest
     * chest storage, as on vanilla's own screens for them, so a mod that acts on those (a
     * sort button, a lock) finds them with nothing written for this mod (Trev, 2026-10-23).
     * A bundle has no vanilla screen, so no vanilla category; ponytail: its own until
     * SlotKit has a standard one for bundles.
     */
    private static final Map<Openable, SlotGroupCategory> CATEGORY = new EnumMap<>(Map.of(
            Openable.SHULKER, SlotGroupCategory.SHULKER_STORAGE,
            Openable.BUNDLE, new SlotGroupCategory(OpenAnywhere.MOD_ID, "bundle"),
            Openable.ENDER_CHEST, SlotGroupCategory.CHEST_STORAGE));

    private static final Map<Openable, OnDemandGroup> GROUPS = new EnumMap<>(Openable.class);

    /** The group of {@code kind} open in lane {@code lane}: what its slots, routes and the screen's grid name. */
    public static SlotGroupId.Created id(Openable kind, int lane) {
        return GROUPS.get(kind).instanceId(lane);
    }

    /** The lane of {@code kind} whose group {@code slot} belongs to, or -1 for none. */
    public static int laneOf(Slot slot, Openable kind) {
        OnDemandGroup.Instance instance = OnDemandGroup.instanceOf(slot);
        return instance != null && instance.kind() == GROUPS.get(kind) ? instance.number() : -1;
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
            GROUPS.put(kind, OnDemandGroup.register(OpenAnywhere.MOD_ID + ":" + kind.name().toLowerCase(java.util.Locale.ROOT),
                    SlotSpec.at(GROUP, CATEGORY.get(kind)).count(kind.slots).shiftClickIn(true)));
        }

        bundleRules();
        // A box refuses what vanilla's shulker box slot refuses: other boxes, anything that
        // can't go in a container item. Declared on the kind, so every open box has it.
        for (int i = 0; i < Openable.SHULKER.slots; i++) {
            Window.slot(ModeScope.BOTH, Address.createdSlot(GROUPS.get(Openable.SHULKER).id(), i)).gate(new SlotGate() {
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

    /**
     * Adds {@code stack}'s slots to the player's open menu, a bundle's sized to the bundle.
     * The lane it took, or -1 when the player's client can't show it.
     */
    static int add(ServerPlayer player, Openable kind, ItemStack stack) {
        BundleStorage bundle = kind == Openable.BUNDLE ? new BundleStorage(player, stack) : null;
        Storage storage = switch (kind) {
            case SHULKER -> new ShulkerStorage(stack);
            case BUNDLE -> bundle;
            case ENDER_CHEST -> new EnderChestStorage(player);
        };
        OnDemandGroup.Instance instance = GROUPS.get(kind).add(player, storage);
        if (instance == null) return -1;
        if (bundle != null) bundle.shownIn(instance);
        return instance.number();
    }

    /** Removes lane {@code lane}'s slots of {@code kind} from {@code menu}, if they're still there. */
    static void remove(AbstractContainerMenu menu, Openable kind, int lane) {
        for (OnDemandGroup.Instance i : GROUPS.get(kind).instances(menu)) if (i.number() == lane) i.remove();
    }

    /** The lane of the open bundle {@code slot} belongs to, or -1. */
    private static int bundleLane(@Nullable Slot slot) {
        return slot == null ? -1 : laneOf(slot, Openable.BUNDLE);
    }

    /**
     * Each slot's rule in an open bundle, set on the kind so every open bundle has it,
     * following a bundle in hand:
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
            Window.slot(ModeScope.BOTH, Address.createdSlot(GROUPS.get(Openable.BUNDLE).id(), index)).gate(new SlotGate() {
                @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
                    BundleContents contents = BundleView.contents(ctx.actingPlayer(), bundleLane(ctx.slot()));
                    if (contents == null) return false;
                    if (index == BundleView.addSlot(contents)) return BundleView.fit(contents, stack) > 0;
                    if (index >= contents.size() || BundleView.sameItem(contents, index, stack)) return false;
                    return BundleView.fitWithout(contents, index, stack) >= stack.getCount();
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return true;
                }
                @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                    GatingContext ctx = GatingContext.current();
                    BundleContents contents = BundleView.contents(ctx.actingPlayer(), bundleLane(ctx.slot()));
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
        for (int i = open.size() - 1; i >= 0; i--) to.add(Dest.group(id(open.get(i).kind(), open.get(i).lane())));
        if (ender != null && isIn(source, Openable.ENDER_CHEST)) {
            to.addAll(back(ender));
            return to;
        }
        if (ender != null) to.add(Dest.group(id(Openable.ENDER_CHEST, 0)));
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
