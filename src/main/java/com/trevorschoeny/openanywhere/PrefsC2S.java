package com.trevorschoeny.openanywhere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player's settings that shape their row: every box gets a tab, and which kinds open
 * at all (a bit per {@link Openable}, by ordinal). Sent on joining and on every change.
 * A preference, not a permission: the server builds the row the player asks for.
 */
public record PrefsC2S(boolean everyBox, int kinds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PrefsC2S> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "prefs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrefsC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PrefsC2S::everyBox, ByteBufCodecs.VAR_INT, PrefsC2S::kinds, PrefsC2S::new);

    /** Whether {@code kind} opens. */
    public boolean opens(Openable kind) {
        return (kinds & (1 << kind.ordinal())) != 0;
    }

    /** What a player who sent nothing gets: every kind, tabs only for what they open. */
    public static final PrefsC2S DEFAULT = new PrefsC2S(false, (1 << Openable.values().length) - 1);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
