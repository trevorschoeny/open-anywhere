package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Who has what open, on this side, in the order it opened. The server's entries are the
 * truth; the client mirrors its own from {@link SessionS2C} so its slot lock, grey mark
 * and shift-click routes predict the server's answers.
 *
 * <p>Any number per player, one per lane (design, Rule 2), plus at most one opened as
 * vanilla's own screen. Keyed weakly by player, so a player object that's gone (respawn,
 * disconnect) takes its sessions with it.
 *
 * <p>One table per side. In singleplayer both sides run in one game, and a player is
 * equal to any player with the same entity id ({@code Entity.equals}), so the client's
 * player and the server's are the same key: one shared table let the client's mirror
 * overwrite the server's session (found 2026-10-16, when the server saw the client's
 * menu).
 */
public final class Sessions {

    private Sessions() {}

    private static final Map<Player, List<Session>> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, List<Session>> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Player, List<Session>> side(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }

    /** The session in {@code kind}'s lane {@code lane} (-1: the one opened natively), or null. */
    public static @Nullable Session at(Player player, Openable kind, int lane) {
        for (Session s : of(player)) if (s.sameSlotAs(kind, lane)) return s;
        return null;
    }

    /** The session that has exactly this stack open, or null. */
    public static @Nullable Session byStack(Player player, ItemStack stack) {
        for (Session s : of(player)) if (s.stack() == stack) return s;
        return null;
    }

    /** The player's open sessions, oldest first, a copy. */
    public static List<Session> of(Player player) {
        Map<Player, List<Session>> table = side(player);
        synchronized (table) {
            List<Session> open = table.get(player);
            return open == null ? List.of() : List.copyOf(open);
        }
    }

    /** Records {@code session}, replacing one in the same lane. */
    static void put(Player player, Session session) {
        Map<Player, List<Session>> table = side(player);
        synchronized (table) {
            List<Session> open = table.computeIfAbsent(player, p -> new ArrayList<>());
            open.removeIf(s -> s.sameSlotAs(session.kind(), session.lane()));
            open.add(session);
        }
    }

    static void remove(Player player, Openable kind, int lane) {
        Map<Player, List<Session>> table = side(player);
        synchronized (table) {
            List<Session> open = table.get(player);
            if (open != null) open.removeIf(s -> s.sameSlotAs(kind, lane));
        }
    }

    /**
     * True when some player has the item at this container slot open. The slot lock asks
     * this for every operation, so it is a short scan of a handful of entries. Containers
     * are per side, so both tables can be searched.
     */
    public static boolean isOpenAt(Container container, int containerSlot) {
        return isOpenAt(SERVER, container, containerSlot) || isOpenAt(CLIENT, container, containerSlot);
    }

    /**
     * The client's own mirror only: what the client has heard opened. In singleplayer the
     * integrated server's table holds it a frame earlier, before the client's tab shows.
     */
    public static boolean isOpenOnClient(Container container, int containerSlot) {
        return isOpenAt(CLIENT, container, containerSlot);
    }

    private static boolean isOpenAt(Map<Player, List<Session>> table, Container container, int containerSlot) {
        synchronized (table) {
            for (List<Session> open : table.values()) {
                for (Session s : open) if (s.container() == container && s.containerSlot() == containerSlot) return true;
            }
        }
        return false;
    }

    /** True when some player on the server has exactly this stack open. */
    static boolean isOpenByAnyone(Object stack) {
        synchronized (SERVER) {
            for (List<Session> open : SERVER.values()) for (Session s : open) if (s.stack() == stack) return true;
        }
        return false;
    }
}
