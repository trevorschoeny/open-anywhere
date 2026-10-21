package com.trevorschoeny.shulkersininventory;

import java.util.Map;
import java.util.WeakHashMap;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The stacked shulker box's slots on the server: one per player, reading through to the
 * box the player has open below a chest ({@link Session#stacked()}), and empty with none.
 * The box's own stacks are its {@link ShulkerStorage}, made again whenever another box opens.
 */
final class StackedShulkerStorage implements Storage {

    // Server players only, so one entity id is one player.
    private static final Map<Player, StackedShulkerStorage> BY_PLAYER = new WeakHashMap<>();

    static synchronized StackedShulkerStorage of(Player player) {
        return BY_PLAYER.computeIfAbsent(player, StackedShulkerStorage::new);
    }

    private final Player player;
    private ItemStack box = ItemStack.EMPTY;
    private ShulkerStorage storage;

    private StackedShulkerStorage(Player player) {
        this.player = player;
    }

    /** The open box's storage, or null when no box is stacked. */
    private ShulkerStorage storage() {
        Session session = Sessions.get(player, Openable.SHULKER);
        if (session == null || !session.stacked()) return null;
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
