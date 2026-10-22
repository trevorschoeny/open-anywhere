package com.trevorschoeny.openanywhere;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * The screen a bundle opens on from the inventory: vanilla's own chest menu with no rows,
 * just the player's inventory, so the client shows vanilla's own chest screen and MenuKit's
 * container list puts the bundle above the inventory as its only section, exactly as it
 * does below a chest (Trev, 2026-10-21: the bundle looks like any opened container).
 *
 * <p>Its own menu type only because vanilla's chest types start at one row. The menu and
 * the screen are vanilla's classes, so a mod that asks "is this a chest screen?" says yes.
 */
public final class BundleHost {

    private BundleHost() {}

    public static final MenuType<ChestMenu> TYPE = new MenuType<>(BundleHost::create, FeatureFlags.VANILLA_SET);

    static void register() {
        Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "bundle"), TYPE);
    }

    static ChestMenu create(int id, Inventory inventory) {
        return new ChestMenu(TYPE, id, inventory, new SimpleContainer(0), 0);
    }

    public static boolean is(AbstractContainerMenu menu) {
        // Only a chest menu can be the host; asking the inventory's menu its type throws.
        return menu instanceof ChestMenu && menu.getType() == TYPE;
    }
}
