package com.trevorschoeny.shulkersininventory;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

/**
 * Who has what open, on this side. The server's entries are the truth; the client
 * mirrors its own from {@link SessionS2C} so its panels, gates and slot lock predict
 * the server's answers.
 *
 * <p>At most two per player: the ender chest, and one shulker box or bundle
 * (design, Rule 2). Keyed weakly by player, so a player object that's gone (respawn,
 * disconnect) takes its sessions with it.
 */
public final class Sessions {

    private Sessions() {}

    // Client and server threads use different player keys, but share the map in singleplayer.
    private static final Map<Player, EnumMap<Openable, Session>> OPEN = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Which panels the client shows. The panels' reveal predicates take no player, so
     * the client's own sessions are mirrored here as a plain set.
     */
    public static final Set<Openable> CLIENT_SHOWN = Collections.synchronizedSet(EnumSet.noneOf(Openable.class));

    public static @Nullable Session get(Player player, Openable kind) {
        synchronized (OPEN) {
            EnumMap<Openable, Session> open = OPEN.get(player);
            return open == null ? null : open.get(kind);
        }
    }

    /** The player's open sessions, a copy. */
    public static Collection<Session> of(Player player) {
        synchronized (OPEN) {
            EnumMap<Openable, Session> open = OPEN.get(player);
            return open == null ? List.of() : List.copyOf(open.values());
        }
    }

    /** Opens {@code session}, replacing the same kind and, for a shulker or bundle, the other one. */
    static void put(Player player, Session session) {
        synchronized (OPEN) {
            EnumMap<Openable, Session> open = OPEN.computeIfAbsent(player, p -> new EnumMap<>(Openable.class));
            if (!session.kind().isEnderChest()) {
                open.remove(Openable.SHULKER);
                open.remove(Openable.BUNDLE);
            }
            open.put(session.kind(), session);
        }
    }

    static void remove(Player player, Openable kind) {
        synchronized (OPEN) {
            EnumMap<Openable, Session> open = OPEN.get(player);
            if (open != null) open.remove(kind);
        }
    }

    /**
     * True when some player on this side has the item at this container slot open. The
     * slot lock ({@link Panels}) asks this for every operation, so it is a short scan
     * of a handful of entries.
     */
    public static boolean isOpenAt(Container container, int containerSlot) {
        synchronized (OPEN) {
            for (EnumMap<Openable, Session> open : OPEN.values()) {
                for (Session s : open.values()) {
                    if (s.container() == container && s.containerSlot() == containerSlot) return true;
                }
            }
        }
        return false;
    }

    /** True when some player on this side has exactly this stack open. */
    static boolean isOpenByAnyone(Object stack) {
        synchronized (OPEN) {
            for (EnumMap<Openable, Session> open : OPEN.values()) {
                for (Session s : open.values()) if (s.stack() == stack) return true;
            }
        }
        return false;
    }
}
