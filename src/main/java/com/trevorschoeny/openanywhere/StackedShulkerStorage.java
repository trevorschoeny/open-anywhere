package com.trevorschoeny.openanywhere;

import java.util.Map;
import java.util.WeakHashMap;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One box lane's slots on the server: one per player and lane, reading through to the box
 * open in that lane ({@link Session#lane()}), and empty with none. The box's own stacks
 * are its {@link ShulkerStorage}, made again whenever another box opens in the lane.
 */
final class StackedShulkerStorage implements Storage {

    // Server players only, so one entity id is one player.
    private static final Map<Player, StackedShulkerStorage[]> BY_PLAYER = new WeakHashMap<>();

    static synchronized StackedShulkerStorage of(Player player, int lane) {
        StackedShulkerStorage[] lanes = BY_PLAYER.computeIfAbsent(player, p -> new StackedShulkerStorage[Openable.SHULKER.lanes]);
        if (lanes[lane] == null) lanes[lane] = new StackedShulkerStorage(player, lane);
        return lanes[lane];
    }

    private final Player player;
    private final int lane;
    private ItemStack box = ItemStack.EMPTY;
    private ShulkerStorage storage;

    private StackedShulkerStorage(Player player, int lane) {
        this.player = player;
        this.lane = lane;
    }

    /** The open box's storage, or null when no box is stacked. */
    private ShulkerStorage storage() {
        Session session = Sessions.at(player, Openable.SHULKER, lane);
        if (session == null) return null;
        if (session.stack() != box) {
            box = session.stack();
            storage = new ShulkerStorage(box);
        }
        return storage;
    }

    @Override
    public ItemStack getStack(int i) {
        ShulkerStorage s = storage();
        return s == null ? ItemStack.EMPTY : s.getStack(i);
    }

    @Override
    public void setStack(int i, ItemStack stack) {
        ShulkerStorage s = storage();
        if (s != null) s.setStack(i, stack);
    }

    @Override
    public int size() {
        return Openable.SHULKER.slots;
    }

    @Override
    public void markDirty() {
        ShulkerStorage s = storage();
        if (s != null) s.markDirty();
    }
}
