package com.trevorschoeny.shulkersininventory;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

/**
 * Who has what open, on this side. The server's entries are the truth; the client
 * mirrors its own from {@link SessionS2C} so its slot lock, grey mark and shift-click
 * routes predict the server's answers.
 *
 * <p>At most two per player: the ender chest, and one shulker box or bundle
 * (design, Rule 2). Keyed weakly by player, so a player object that's gone (respawn,
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

    private static final Map<Player, EnumMap<Openable, Session>> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, EnumMap<Openable, Session>> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Player, EnumMap<Openable, Session>> side(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }


    public static @Nullable Session get(Player player, Openable kind) {
        Map<Player, EnumMap<Openable, Session>> table = side(player);
        synchronized (table) {
            EnumMap<Openable, Session> open = table.get(player);
            return open == null ? null : open.get(kind);
        }
    }

    /** The player's open sessions, a copy. */
    public static Collection<Session> of(Player player) {
        Map<Player, EnumMap<Openable, Session>> table = side(player);
        synchronized (table) {
            EnumMap<Openable, Session> open = table.get(player);
            return open == null ? List.of() : List.copyOf(open.values());
        }
    }

    /** Records {@code session}, replacing one of the same kind. */
    static void put(Player player, Session session) {
        Map<Player, EnumMap<Openable, Session>> table = side(player);
        synchronized (table) {
            EnumMap<Openable, Session> open = table.computeIfAbsent(player, p -> new EnumMap<>(Openable.class));
            open.put(session.kind(), session);
        }
    }

    static void remove(Player player, Openable kind) {
        Map<Player, EnumMap<Openable, Session>> table = side(player);
        synchronized (table) {
            EnumMap<Openable, Session> open = table.get(player);
            if (open != null) open.remove(kind);
        }
    }

    /**
     * True when some player has the item at this container slot open. The slot lock
     * ({@link Panels}) asks this for every operation, so it is a short scan of a
     * handful of entries. Containers are per side, so both tables can be searched.
     */
    public static boolean isOpenAt(Container container, int containerSlot) {
        return isOpenAt(SERVER, container, containerSlot) || isOpenAt(CLIENT, container, containerSlot);
    }

    private static boolean isOpenAt(Map<Player, EnumMap<Openable, Session>> table, Container container, int containerSlot) {
        synchronized (table) {
            for (EnumMap<Openable, Session> open : table.values()) {
                for (Session s : open.values()) {
                    if (s.container() == container && s.containerSlot() == containerSlot) return true;
                }
            }
        }
        return false;
    }

    /** True when some player on the server has exactly this stack open. */
    static boolean isOpenByAnyone(Object stack) {
        synchronized (SERVER) {
            for (EnumMap<Openable, Session> open : SERVER.values()) {
                for (Session s : open.values()) if (s.stack() == stack) return true;
            }
        }
        return false;
    }
}
