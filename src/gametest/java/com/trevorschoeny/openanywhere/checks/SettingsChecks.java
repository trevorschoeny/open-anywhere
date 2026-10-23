package com.trevorschoeny.openanywhere.checks;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.openanywhere.OpenAnywhere;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The Open Anywhere tab in Inventory Plus's settings menu opens, screenshotted for a look.
 * Runs only with Inventory Plus loaded: {@code -PwithIp} on the gametest run. A plain run
 * has no Inventory Plus, as a player without it, and skips this.
 */
public class SettingsChecks implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded("inventoryplus")) return;
        ctx.setScreen(() -> InventoryPlusApi.settingsScreen(null, OpenAnywhere.MOD_ID));
        ctx.waitTicks(5);
        String screen = ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen()));
        ctx.takeScreenshot("settings-tab");
        ctx.setScreen(() -> null);
        if (screen.equals("null")) throw new AssertionError("Inventory Plus's settings menu didn't open on Open Anywhere's tab");
    }
}
