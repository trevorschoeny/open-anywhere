package com.trevorschoeny.openanywhere;

import com.trevorschoeny.openanywhere.mixin.ItemLayerAccessor;
import com.trevorschoeny.openanywhere.mixin.ItemStateAccessor;

import org.joml.Vector3f;

import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * An item drawn turned in the GUI: the Inventory tab's head, which turns to face the player
 * while its group is open.
 *
 * <p>While {@link #draw} runs, every item it draws has the turn added to its layers' GUI
 * display rotation (a head's is {@code [30, 45, 0]}; it faces the viewer at {@code [0, 0, 0]}),
 * so the model turns about its center as a resource pack's rotation would turn it. The turn
 * joins the item's model identity, so the GUI's item atlas keeps each angle apart, and the
 * item is drawn as oversized, so a corner turned past the 16 px box still shows.
 *
 * <p>Angles are rounded to whole degrees, so a turn makes a bounded number of atlas entries.
 * Render thread only.
 */
public final class ItemTurn {

    private ItemTurn() {}

    // The turn in force for items drawn now, in whole degrees; all 0 outside draw().
    private static int pitch, yaw;
    // Whether items drawn now are lit flat, evenly from the front, as a flat item is.
    private static boolean flat;

    /** What sets one angle's atlas entry apart from another's. */
    private record Identity(int pitch, int yaw, boolean flat) {}

    /** Runs {@code draw}, every item it draws turned by {@code pitch} and {@code yaw} degrees. */
    public static void draw(float pitch, float yaw, Runnable draw) {
        draw(pitch, yaw, false, draw);
    }

    /**
     * As {@link #draw(float, float, Runnable)}, lit flat when {@code flat}: evenly from the
     * front, so a face turned to the viewer reads in full, rather than vanilla's 3D GUI light,
     * which falls from the upper left and leaves a front face dim.
     */
    public static void draw(float pitch, float yaw, boolean flat, Runnable draw) {
        boolean f = ItemTurn.flat;
        ItemTurn.flat = flat;
        int p = ItemTurn.pitch, y = ItemTurn.yaw;
        ItemTurn.pitch = Math.round(pitch);
        ItemTurn.yaw = Math.round(yaw);
        try {
            draw.run();
        } finally {
            ItemTurn.pitch = p;
            ItemTurn.yaw = y;
            ItemTurn.flat = f;
        }
    }

    /** {@code output}, just resolved for the GUI: turned while a turn is in force. */
    public static void apply(ItemStackRenderState output) {
        if (pitch == 0 && yaw == 0 && !flat) return;
        ItemStateAccessor state = (ItemStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers = state.openanywhere$layers();
        if (flat) for (int i = 0; i < state.openanywhere$activeLayerCount(); i++) layers[i].setUsesBlockLight(false);
        if (pitch == 0 && yaw == 0) {
            output.appendModelIdentityElement(new Identity(0, 0, true));
            return;
        }
        for (int i = 0; i < state.openanywhere$activeLayerCount(); i++) {
            ItemTransform t = ((ItemLayerAccessor) layers[i]).openanywhere$itemTransform();
            Vector3f rotation = new Vector3f(t.rotation()).add(pitch, yaw, 0);
            layers[i].setItemTransform(new ItemTransform(rotation, t.translation(), t.scale()));
        }
        output.appendModelIdentityElement(new Identity(pitch, yaw, flat));
        output.setOversizedInGui(true);
    }
}
