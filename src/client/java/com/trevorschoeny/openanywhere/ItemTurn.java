package com.trevorschoeny.openanywhere;

import com.trevorschoeny.openanywhere.mixin.ItemLayerAccessor;
import com.trevorschoeny.openanywhere.mixin.ItemStateAccessor;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;

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
    // The light items drawn now are lit by, or null for vanilla's.
    private static @Nullable Light light;

    /**
     * A light of an item's own: the shader's two lights, each shining along its direction in
     * the GUI's item space (+z toward the viewer, -y up). A face takes 0.4 of its colour,
     * plus 0.6 for each light by how square it faces that light, up to its full colour.
     */
    public record Light(Vector3fc first, Vector3fc second) {}

    /** The items drawn turned, each with its own light, until the GUI has drawn them. */
    private static final Map<ItemStackRenderState, Light> LIT = Collections.synchronizedMap(new WeakHashMap<>());
    /** One light buffer per direction, made on first use. */
    private static final Map<Light, GpuBufferSlice> BUFFERS = new HashMap<>();

    /** What sets one angle's atlas entry apart from another's. */
    private record Identity(int pitch, int yaw, @Nullable Light light) {}

    /** Runs {@code draw}, every item it draws turned by {@code pitch} and {@code yaw} degrees. */
    public static void draw(float pitch, float yaw, Runnable draw) {
        draw(pitch, yaw, null, draw);
    }

    /**
     * As {@link #draw(float, float, Runnable)}, lit by {@code light} rather than vanilla's GUI
     * light, which falls from the upper left and leaves a face turned to the viewer dim.
     * Null: vanilla's.
     */
    public static void draw(float pitch, float yaw, @Nullable Light light, Runnable draw) {
        Light l = ItemTurn.light;
        int p = ItemTurn.pitch, y = ItemTurn.yaw;
        ItemTurn.light = light;
        ItemTurn.pitch = Math.round(pitch);
        ItemTurn.yaw = Math.round(yaw);
        try {
            draw.run();
        } finally {
            ItemTurn.pitch = p;
            ItemTurn.yaw = y;
            ItemTurn.light = l;
        }
    }

    /** Called with each item's render state as it's resolved: adds the turn and notes the light. */
    public static void apply(ItemStackRenderState output) {
        if (pitch == 0 && yaw == 0 && light == null) return;
        if (light != null) LIT.put(output, light);
        output.appendModelIdentityElement(new Identity(pitch, yaw, light));
        if (pitch == 0 && yaw == 0) return;
        ItemStateAccessor state = (ItemStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers = state.openanywhere$layers();
        for (int i = 0; i < state.openanywhere$activeLayerCount(); i++) {
            ItemTransform t = ((ItemLayerAccessor) layers[i]).openanywhere$itemTransform();
            Vector3f rotation = new Vector3f(t.rotation()).add(pitch, yaw, 0);
            layers[i].setItemTransform(new ItemTransform(rotation, t.translation(), t.scale()));
        }
        output.setOversizedInGui(true);
    }

    /**
     * The GUI is about to light {@code item} ({@code ItemLightMixin}): with its own light when
     * it has one. False: vanilla lights it.
     */
    public static boolean light(@Nullable ItemStackRenderState item) {
        Light l = item == null ? null : LIT.get(item);
        if (l == null) return false;
        RenderSystem.setShaderLights(BUFFERS.computeIfAbsent(l, dir -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, Lighting.UBO_SIZE).putVec3(dir.first()).putVec3(dir.second()).get();
                // Lighting's own usage for its buffer: a uniform, written once.
                return RenderSystem.getDevice().createBuffer(() -> "Open Anywhere item light", 136, data).slice();
            }
        }));
        return true;
    }
}
