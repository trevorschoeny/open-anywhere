package com.trevorschoeny.openanywhere.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.LayoutPrototype;
import com.trevorschoeny.openanywhere.LayoutPrototype.Layout;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Sessions;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import testgallery.SceneWorld;

/**
 * The layout prototype (scene open-anywhere/layouts): a chest with two boxes opened from it,
 * the ender chest with a box opened from inside it, and a box from the hotbar, then each
 * layout in turn, checked for what it shows and screenshotted for the eye.
 */
public class LayoutChecks implements FabricClientGameTest {

    private static final int SDL_ALT = 0x100;

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulkers-in-inventory", "layouts")) {
            this.scene = scene;
            // Scale 3 (640 x 360 at 1920 x 1080): room for two panels side by side.
            int scale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
            ctx.runOnClient(mc -> { mc.options.guiScale().set(3); mc.resizeGui(); });
            var station = scene.station("openanywhere:layouts");
            JsonArray o = station.getAsJsonArray("origin");
            BlockPos chest = new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
            scene.world().getServer().runCommand(String.format("tp @p %.1f %d %.1f 0 30", chest.getX() + 0.5, chest.getY(), chest.getZ() - 1.5));
            ctx.waitTicks(3);
            ctx.getInput().lookAt(chest);
            ctx.waitTick();
            ctx.getInput().pressKey(opt -> opt.keyUse);
            ctx.waitTicks(5);
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen)) throw new AssertionError("the chest didn't open");

            LayoutPrototype.layout = Layout.SCROLL_TABS;
            // The ender chest and its box first, while the list is short enough to show them.
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 1, "the ender chest");
            open(s -> Groups.isIn(s, Openable.ENDER_CHEST) && Openable.of(s.getItem()) == Openable.SHULKER, "the green box in the ender chest");
            ctx.runOnClient(mc -> com.trevlar.menukit.inject.ContainerRegions.setScroll(0));
            ctx.waitTicks(2);
            open(s -> isChest(s) && s.getContainerSlot() == 0, "the red box");
            open(s -> isChest(s) && s.getContainerSlot() == 1, "the orange box");
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box");
            int open = scene.world().getServer().computeOnServer(sv -> Sessions.of(sv.getPlayerList().getPlayers().getFirst()).size());
            if (open != 5) fail(open + " things open, not 5");

            // Tabs with scroll: chest, its two boxes, ender chest, its box, the lime box.
            List<String> tabs = ctx.computeOnClient(mc -> LayoutPrototype.tabIdsForChecks(false));
            if (tabs.size() != 6) fail("tabs with scroll shows " + tabs.size() + " tabs, not 6: " + tabs);
            ctx.takeScreenshot("layouts-1-tabs-scroll");
            // Click the last tab: the list scrolls until the lime box shows.
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(false, tabs.size() - 1));
            ctx.waitTicks(40);
            if (!shown(tabs.getLast())) fail("clicking the last tab didn't scroll to its section");
            ctx.takeScreenshot("layouts-1-tabs-scroll-jumped");

            // Tabs, no scroll: one container and one item from it.
            LayoutPrototype.layout = Layout.TABS;
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(false, 2));   // the chest's second box
            ctx.waitTicks(5);
            int showing = 0;
            for (String id : tabs) if (shown(id)) showing++;
            if (showing != 2) fail("tabs without scroll shows " + showing + " sections, not 2");
            if (!shown(tabs.get(0)) || !shown(tabs.get(2))) fail("tabs without scroll didn't show the chest and the picked box together");
            ctx.takeScreenshot("layouts-2-tabs-no-scroll");

            // Two panels: the lime box moves to the right panel, still shown.
            LayoutPrototype.layout = Layout.TWO_PANELS;
            ctx.waitTicks(5);
            List<String> right = ctx.computeOnClient(mc -> LayoutPrototype.tabIdsForChecks(true));
            if (right.size() != 1) fail("the right panel has " + right.size() + " tabs, not 1");
            if (shown(tabs.getLast())) fail("the lime box is still in the container list in two panels");
            ctx.takeScreenshot("layouts-3-two-panels");
            LayoutPrototype.layout = Layout.SCROLL_TABS;
            ctx.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
        }
        if (!failures.isEmpty()) throw new AssertionError("layout checks failed:\n- " + String.join("\n- ", failures));
    }

    private boolean shown(String sectionId) {
        return ctx.computeOnClient(mc -> ContainerRegion.anchor(sectionId).box(mc.gui.screen()) != null);
    }

    private static boolean isChest(Slot s) {
        return !(s.container instanceof Inventory) && com.trevlar.slotkit.api.slot.CreatedSlot.of(s) == null;
    }

    private void fail(String what) { failures.add(what); }

    /** An Option-click on the first shown slot matching {@code p}. */
    private void open(Predicate<Slot> p, String what) {
        double[] at = ctx.computeOnClient(mc -> {
            var screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot found = null;
            for (Slot s : screen.getMenu().slots) if (s.isActive() && p.test(s)) { found = s; break; }
            if (found == null) throw new AssertionError("no " + what + " on the screen");
            var edge = (ScreenAccessor) screen;
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(edge.openanywhere$leftPos() + found.x + 8) * f, (edge.openanywhere$topPos() + found.y + 8) * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        for (int action : new int[] {1, 0}) {
            ctx.runOnClient(mc -> ((net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler)
                    .invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, SDL_ALT), action));
            ctx.waitTick();
        }
        ctx.waitTicks(5);
    }
}
