package com.trevorschoeny.openanywhere;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The tab icon of a screen opened on an entity, the one place it's chosen: a villager's or a
 * wandering trader's own head, drawn as the Inventory tab's player head is (the client's
 * {@code HeadModels} draws it), else the entity's own item (a minecart with a chest, a
 * horse's spawn egg).
 *
 * <p>A head is a spawn egg wearing this mod's item model, so it travels in the row as any
 * icon does; a villager's carries its biome, profession and level ({@link #VILLAGER}), so
 * the head shows the one the player is trading with.
 */
public final class Heads {

    private Heads() {}

    /** The villager a head shows. Both sides register it: the server writes it, the client's head reads it. */
    public static final DataComponentType<VillagerData> VILLAGER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "villager"),
            DataComponentType.<VillagerData>builder().persistent(VillagerData.CODEC).networkSynchronized(VillagerData.STREAM_CODEC).build());

    /** The item models of the two heads ({@code assets/openanywhere/items/}). */
    public static final Identifier VILLAGER_HEAD = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "villager_head");
    public static final Identifier TRADER_HEAD = Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "wandering_trader_head");

    /** Common init: loads the class, so the component is registered before anything reads one. */
    static void register() {}

    /** The icon of a screen opened on {@code entity}; empty when it has no item. */
    public static ItemStack of(Entity entity) {
        if (entity instanceof Villager villager) {
            ItemStack head = head(Items.VILLAGER_SPAWN_EGG, VILLAGER_HEAD);
            head.set(VILLAGER, villager.getVillagerData());
            return head;
        }
        if (entity instanceof WanderingTrader) return head(Items.WANDERING_TRADER_SPAWN_EGG, TRADER_HEAD);
        ItemStack item = entity.getPickResult();
        return item == null ? ItemStack.EMPTY : item;
    }

    /** Whether {@code icon} is one of these heads: the client draws it turning, as the player's head. */
    public static boolean is(ItemStack icon) {
        Identifier model = icon.get(DataComponents.ITEM_MODEL);
        return VILLAGER_HEAD.equals(model) || TRADER_HEAD.equals(model);
    }

    private static ItemStack head(net.minecraft.world.item.Item egg, Identifier model) {
        ItemStack head = new ItemStack(egg);
        head.set(DataComponents.ITEM_MODEL, model);
        return head;
    }
}
