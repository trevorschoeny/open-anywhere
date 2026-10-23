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
 * The screen items open on from the inventory: vanilla's own chest menu with no rows, just
 * the player's inventory, so the client shows vanilla's own chest screen and MenuKit's
 * container list shows each opened item above the inventory as a tab, exactly as on a
 * chest. Boxes, bundles and the ender chest opened from the inventory are tabs on one
 * screen, so several can be open at once there too.
 *
 * <p>Its own menu type only because vanilla's chest types start at one row. The menu and
 * the screen are vanilla's classes, so a mod that asks "is this a chest screen?" says yes.
 */
public final class ContainerHost {

    private ContainerHost() {}

    public static final MenuType<ChestMenu> TYPE = new MenuType<>(ContainerHost::create, FeatureFlags.VANILLA_SET);

    static void register() {
        Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "host"), TYPE);
    }

    static ChestMenu create(int id, Inventory inventory) {
        return new ChestMenu(TYPE, id, inventory, new SimpleContainer(0), 0);
    }

    public static boolean is(AbstractContainerMenu menu) {
        // Only a chest menu can be the host; asking the inventory's menu its type throws.
        return menu instanceof ChestMenu && menu.getType() == TYPE;
    }
}
