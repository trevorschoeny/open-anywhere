package com.trevorschoeny.openanywhere;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * A screen opened in the world reaches the client as one bundle: the old screen's close, the
 * new one's open and contents, and its tab row ({@link ServerTabs#opened}). The client handles a
 * bundle in one go, so no frame is drawn between the screen and its row. Server thread only.
 */
public final class ScreenBundle {

    private ScreenBundle() {}

    // The packets held back for each connection while its player opens a screen.
    private static final Map<ServerCommonPacketListenerImpl, List<Packet<? super ClientGamePacketListener>>> HELD = new IdentityHashMap<>();

    /** Runs {@code open} for {@code player}, everything it sends going out as one bundle. */
    public static <T> T around(ServerPlayer player, Supplier<T> open) {
        ServerCommonPacketListenerImpl connection = player.connection;
        // Nested (an open inside an open): the outer one sends the bundle.
        if (connection == null || HELD.containsKey(connection)) return open.get();
        List<Packet<? super ClientGamePacketListener>> held = new ArrayList<>();
        HELD.put(connection, held);
        try {
            return open.get();
        } finally {
            HELD.remove(connection);
            if (held.size() == 1) connection.send(held.getFirst());
            else if (!held.isEmpty()) connection.send(new ClientboundBundlePacket(held));
        }
    }

    /** {@code packet} is about to go out on {@code connection}: true when it's held for the bundle instead. */
    @SuppressWarnings("unchecked")
    public static boolean hold(ServerCommonPacketListenerImpl connection, Packet<?> packet) {
        List<Packet<? super ClientGamePacketListener>> held = HELD.get(connection);
        if (held == null || packet.isTerminal()) return false;
        // A bundle can't hold a bundle: its packets join this one, in order.
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> p : bundle.subPackets()) held.add((Packet<? super ClientGamePacketListener>) p);
        } else {
            held.add((Packet<? super ClientGamePacketListener>) packet);
        }
        return true;
    }
}
