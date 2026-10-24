package com.trevorschoeny.openanywhere;

import java.util.List;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.CreatedSlot;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * The slots a bundle opens in. A shulker box and the ender chest open on vanilla's own
 * menus for them; a bundle has none, so it opens on the host ({@link ContainerHost}), with
 * its slots a SlotKit {@link OnDemandGroup} added when it opens: one slot per stack, in the
 * bundle's order, then the slot that adds one.
 *
 * <p>Also here, because both sides need them: the lock on the open item's own slot, the
 * bundle's per-slot rules, and where a shift-click goes on the host.
 */
public final class Groups {

    private Groups() {}

    /** The group's id within its owner: {@code openanywhere:bundle_0:slots}. */
    public static final String GROUP = "slots";

    /**
     * The bundle's slots, as other mods see them: SlotKit's bundle storage, the category any
     * mod's opened bundle shares, so a sort button finds them with nothing written for this mod.
     */
    private static OnDemandGroup bundle;

    /** The open bundle's slot group: what its slots, routes and the host's grid name. One bundle is open at a time. */
    public static SlotGroupId.Created bundleId() {
        return bundle.instanceId(0);
    }

    /** {@code slot}'s place in the open bundle, or -1 when it isn't one of the bundle's slots. */
    public static int bundleIndex(@Nullable Slot slot) {
        OnDemandGroup.Instance instance = OnDemandGroup.instanceOf(slot);
        if (instance == null || instance.kind() != bundle) return -1;
        CreatedSlot created = CreatedSlot.of(slot);
        return created == null ? -1 : created.getLocalIndex();
    }

    static void register() {
        bundle = OnDemandGroup.register(OpenAnywhere.MOD_ID + ":bundle",
                SlotSpec.at(GROUP, SlotGroupCategory.BUNDLE_STORAGE).count(Openable.BUNDLE.slots).shiftClickIn(true));
        bundleRules();

        // The open item can be picked up from under its own screen. While it's off its place,
        // its screen's slots refuse every operation, from vanilla's clicks, keys and drops to
        // any other mod's (SlotKit's slot lock), and show it as it was. Both modes.
        SlotOperations.veto(ModeScope.BOTH, (ref, operation) -> Shown.locked(ref.container(), ref.player()));

        QuickMoveRoutes.register(Groups::route);
    }

    /**
     * Adds the bundle's slots over {@code storage} to the host the player has open, sized to
     * the bundle: its slots, or null when the player's client can't show them.
     */
    static OnDemandGroup.@Nullable Instance addBundle(ServerPlayer player, Storage storage, BundleStorage contents) {
        OnDemandGroup.Instance instance = bundle.add(player, storage);
        if (instance != null) contents.shownIn(instance);
        return instance;
    }

    /**
     * Each slot's rule in an open bundle, following a bundle in hand:
     * <ul>
     *   <li>The add slot takes as much as vanilla's 64-weight rule allows.</li>
     *   <li>A filled slot takes more of its own item, as much as the 64-weight rule allows,
     *       so half a stack taken out goes back where it came from.</li>
     *   <li>A filled slot swaps with a different item on the cursor, when that item fits
     *       the bundle once the slot's stack is out.</li>
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
                    if (index >= contents.size()) return false;
                    if (BundleView.sameItem(contents, index, stack)) return BundleView.fit(contents, stack) > 0;
                    return BundleView.fitWithout(contents, index, stack) >= stack.getCount();
                }
                @Override public boolean mayPickup(Player player, GatingContext ctx) {
                    return true;
                }
                @Override public int maxStackSize(ItemStack stack, int vanillaMax) {
                    BundleContents contents = BundleView.contents(GatingContext.current().actingPlayer());
                    if (contents == null) return vanillaMax;
                    if (index >= contents.size()) return Math.min(vanillaMax, BundleView.fit(contents, stack));
                    // Asked about the slot's own item (a merge): what it holds, plus the room left for that item.
                    if (BundleView.sameItem(contents, index, stack))
                        return Math.min(vanillaMax, contents.items().get(index).count() + BundleView.fit(contents, stack.copyWithCount(vanillaMax)));
                    // Asked about a different stack (a swap): what fits in its place.
                    return Math.min(vanillaMax, BundleView.fitWithout(contents, index, stack));
                }
            });
        }
    }

    /**
     * Shift-click on the host: from the inventory into the bundle, and out of the bundle back
     * to the inventory. What doesn't fit goes where vanilla sends it. The other screens are
     * vanilla's own, with vanilla's shift-click.
     */
    private static @Nullable List<Dest> route(AbstractContainerMenu menu, Player player, Slot source) {
        if (!ContainerHost.is(menu) || bundle.instances(menu).isEmpty()) return null;
        if (bundleIndex(source) >= 0) return List.of(Dest.PLAYER);
        return List.of(Dest.group(bundleId()), Dest.VANILLA);
    }
}
