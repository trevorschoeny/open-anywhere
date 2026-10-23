package com.trevorschoeny.openanywhere;

import java.util.Optional;

import com.trevlar.slotkit.api.item.ItemPath;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One tab on the row: the player's inventory, the container they opened in the world, or
 * an item, named by where it sits ({@link ItemPath}: the inventory, the world container or
 * the ender chest, then the slot inside each box on the way down).
 *
 * <p>Every tab is a real screen: the inventory's, the chest's, a shulker box's, the ender
 * chest's, or the bundle's host ({@link ContainerHost}). Both sides name tabs this way, so a
 * click names one to the server and the server's row names them to the client.
 *
 * @param path the item's place; null for the inventory and the world tab
 */
public record TabId(Type type, @Nullable ItemPath path) {

    public enum Type { INVENTORY, WORLD, ITEM }

    public static final TabId INVENTORY = new TabId(Type.INVENTORY, null);
    public static final TabId WORLD = new TabId(Type.WORLD, null);

    public static TabId item(ItemPath path) {
        return new TabId(Type.ITEM, path);
    }

    public static final StreamCodec<ByteBuf, TabId> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Type.values()[Math.floorMod(i, Type.values().length)], Type::ordinal), TabId::type,
            ByteBufCodecs.optional(ItemPath.STREAM_CODEC), id -> Optional.ofNullable(id.path()),
            (type, path) -> new TabId(type, path.orElse(null)));
}
