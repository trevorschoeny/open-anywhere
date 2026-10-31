package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.trevorschoeny.openanywhere.mixin.SpecialRenderersAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection.Hat;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/**
 * The villager's and the wandering trader's heads as item models ({@link Heads}): vanilla's
 * own head parts and skins, layered as vanilla layers them on the entity, drawn where a
 * player head's skull is drawn. Being items, they turn, light and shadow through the same
 * code as the Inventory tab's player head ({@code ClientPanels.drawHead}).
 */
final class HeadModels {

    private HeadModels() {}

    private static final Identifier VILLAGER_SKIN = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final Identifier TRADER_SKIN = Identifier.withDefaultNamespace("textures/entity/wandering_trader/wandering_trader.png");

    /** Client init: the two heads' model types, named as their item models are. Before the first resource load. */
    static void register() {
        var types = SpecialRenderersAccessor.openanywhere$types();
        types.put(Heads.VILLAGER_HEAD, VillagerUnbaked.CODEC);
        types.put(Heads.TRADER_HEAD, TraderUnbaked.CODEC);
    }

    /** A layer's head: the model layer's {@code head} part, its hat and nose with it. */
    private static Model.Simple head(EntityModelSet models, ModelLayerLocation layer) {
        return new Model.Simple(models.bakeLayer(layer).getChild("head"), RenderTypes::entityCutout);
    }

    private static void extents(Model.Simple head, Consumer<Vector3fc> out) {
        head.root().getExtentsForGui(new PoseStack(), out);
    }

    // The villager's

    private record VillagerUnbaked() implements SpecialModelRenderer.Unbaked<VillagerData> {
        static final MapCodec<VillagerUnbaked> CODEC = MapCodec.unit(new VillagerUnbaked());

        @Override
        public SpecialModelRenderer<VillagerData> bake(SpecialModelRenderer.BakingContext context) {
            return new VillagerHead(head(context.entityModelSet(), ModelLayers.VILLAGER),
                    head(context.entityModelSet(), ModelLayers.VILLAGER_NO_HAT));
        }

        @Override
        public MapCodec<VillagerUnbaked> type() {
            return CODEC;
        }
    }

    /**
     * Vanilla's villager skin, its biome's over it, then its profession's (VillagerProfessionLayer,
     * the head's share). The biome's hat stays off under a profession's own (the skins' hat
     * metadata). No level badge: it's on the belt. Baked again on each resource reload, so the
     * hats it has read are the loaded pack's.
     */
    private static final class VillagerHead implements SpecialModelRenderer<VillagerData> {
        private final Model.Simple withHat, noHat;
        private final Map<Identifier, Hat> hats = new HashMap<>();

        VillagerHead(Model.Simple withHat, Model.Simple noHat) {
            this.withHat = withHat;
            this.noHat = noHat;
        }

        @Override
        public @Nullable VillagerData extractArgument(ItemStack stack) {
            return stack.get(Heads.VILLAGER);
        }

        @Override
        public void submit(@Nullable VillagerData data, PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
            out.submitModel(withHat, Unit.INSTANCE, pose, VILLAGER_SKIN, light, overlay, outline);
            if (data == null) return;
            Identifier type = skin("type", data.type());
            Identifier profession = skin("profession", data.profession());
            Hat typeHat = hat(type), professionHat = hat(profession);
            boolean typeKeepsHat = professionHat == Hat.NONE || professionHat == Hat.PARTIAL && typeHat != Hat.FULL;
            out.submitModel(typeKeepsHat ? withHat : noHat, Unit.INSTANCE, pose, type, light, overlay, outline);
            if (!data.profession().is(VillagerProfession.NONE))
                out.submitModel(withHat, Unit.INSTANCE, pose, profession, light, overlay, outline);
        }

        @Override
        public void getExtents(Consumer<Vector3fc> out) {
            extents(withHat, out);
        }

        /** {@code textures/entity/villager/<kind>/<id>.png}, as vanilla names them. */
        private static Identifier skin(String kind, Holder<?> holder) {
            return holder.unwrapKey().map(k -> k.identifier().withPath(p -> "textures/entity/villager/" + kind + "/" + p + ".png"))
                    .orElse(VILLAGER_SKIN);
        }

        private Hat hat(Identifier skin) {
            return hats.computeIfAbsent(skin, s -> Minecraft.getInstance().getResourceManager().getResource(s).flatMap(r -> {
                try {
                    return r.metadata().getSection(VillagerMetadataSection.TYPE).map(VillagerMetadataSection::hat);
                } catch (java.io.IOException unreadable) {
                    return java.util.Optional.empty();
                }
            }).orElse(Hat.NONE));
        }
    }

    // The wandering trader's

    private record TraderUnbaked() implements NoDataSpecialModelRenderer.Unbaked {
        static final MapCodec<TraderUnbaked> CODEC = MapCodec.unit(new TraderUnbaked());

        @Override
        public NoDataSpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            Model.Simple head = head(context.entityModelSet(), ModelLayers.WANDERING_TRADER);
            return new NoDataSpecialModelRenderer() {
                @Override
                public void submit(PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
                    out.submitModel(head, Unit.INSTANCE, pose, TRADER_SKIN, light, overlay, outline);
                }

                @Override
                public void getExtents(Consumer<Vector3fc> out) {
                    extents(head, out);
                }
            };
        }

        @Override
        public MapCodec<TraderUnbaked> type() {
            return CODEC;
        }
    }
}
