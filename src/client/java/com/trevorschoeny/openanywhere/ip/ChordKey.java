package com.trevorschoeny.openanywhere.ip;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;

import net.minecraft.client.KeyMapping;

/**
 * The Open key through Keybindery, which Inventory Plus always brings: true while every key
 * of the mapping's chord is held (Ctrl+X, say, set on the settings tab). Loaded only when
 * Keybindery is, like the rest of this package.
 */
public final class ChordKey {

    private ChordKey() {}

    public static boolean isHeld(KeyMapping mapping) {
        return KeybinderyAPI.getInstance().isHeld(mapping);
    }
}
