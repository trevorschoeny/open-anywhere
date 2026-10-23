package com.trevorschoeny.openanywhere.checks;

import com.trevorschoeny.openanywhere.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The settings open, screenshotted for a look: Open Anywhere's own screen in a plain run,
 * as a player without Inventory Plus has it, and Inventory Plus's menu on the Open Anywhere
 * tab with {@code -PwithIp}.
 */
public class SettingsChecks implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean ip = FabricLoader.getInstance().isModLoaded("inventoryplus");
        ctx.setScreen(() -> SettingsScreen.create(null));
        ctx.waitTicks(5);
        Object screen = ctx.computeOnClient(mc -> mc.gui.screen());
        ctx.takeScreenshot(ip ? "settings-ip-tab" : "settings-screen");
        ctx.setScreen(() -> null);
        if (screen == null) throw new AssertionError("the settings didn't open");
        if (ip == screen instanceof SettingsScreen)
            throw new AssertionError(ip ? "with Inventory Plus, Open Anywhere's own screen opened instead of IP's tab"
                    : "without Inventory Plus, " + screen + " opened instead of Open Anywhere's settings");
    }
}
