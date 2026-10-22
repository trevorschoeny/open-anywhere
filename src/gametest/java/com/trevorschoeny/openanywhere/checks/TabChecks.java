package com.trevorschoeny.openanywhere.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Sessions;
import com.trevorschoeny.openanywhere.Tabs;
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
 * Tabs on a double chest (scene shulkers-in-inventory/tabs): the ender chest, a box inside
 * it, two boxes from the chest and one from the hotbar, each shown on its own as it opens,
 * then the chest again. Checked for what shows and how tall the panel is, and screenshotted
 * at GUI scale 2 (240 px tall, as Trev plays at).
 */
public class TabChecks implements FabricClientGameTest {

    private static final String CHEST = "openanywhere:chest";

    private ClientGameTestContext ctx;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulkers-in-inventory", "tabs")) {
            int scale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
            ctx.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeGui(); });
            var station = scene.station("openanywhere:tabs");
            JsonArray o = station.getAsJsonArray("origin");
            BlockPos chest = new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
            scene.world().getServer().runCommand(String.format("tp @p %.1f %d %.1f 0 30", chest.getX() + 0.5, chest.getY(), chest.getZ() - 1.5));
            ctx.waitTicks(3);
            ctx.getInput().lookAt(chest);
            ctx.waitTick();
            ctx.getInput().pressKey(opt -> opt.keyUse);
            ctx.waitTicks(5);
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen c && c.getMenu().getRowCount() == 6))
                throw new AssertionError("the double chest didn't open");

            // The region comes up with the first item opened; the chest's tab, measured.
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 1, "the ender chest");
            if (!shown("openanywhere:ender_chest_0")) fail("the ender chest wasn't shown as it opened");
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            int chestHeight = height();
            ctx.runOnClient(mc -> Tabs.pick("openanywhere:ender_chest_0"));
            ctx.waitTicks(3);
            open(s -> Groups.isIn(s, Openable.ENDER_CHEST) && Openable.of(s.getItem()) == Openable.SHULKER, "the green box in the ender chest");
            if (!shown("openanywhere:shulker_0_ender_chest")) fail("the green box wasn't shown as it opened");
            int boxHeight = height();
            if (boxHeight >= chestHeight) fail("a box's panel (" + boxHeight + ") isn't shorter than the double chest's (" + chestHeight + ")");
            ctx.takeScreenshot("tabs-box");
            // A box's tab shown: the screen must not be laid out again frame after frame.
            int before = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerRegions.relayouts());
            ctx.waitTicks(20);
            int after = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerRegions.relayouts());
            if (after - before > 2) fail("with a box's tab shown, the screen was laid out again " + (after - before) + " times in 20 ticks");

            open(s -> isChest(s) && idOf(s).equals("red_shulker_box"), "the red box");
            open(s -> isChest(s) && idOf(s).equals("orange_shulker_box"), "the orange box");
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box");
            int open = scene.world().getServer().computeOnServer(sv -> Sessions.of(sv.getPlayerList().getPlayers().getFirst()).size());
            if (open != 5) fail(open + " things open, not 5");

            // The wheel away from the list moves a tab: down to the right, up to the left.
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            List<String> row = ctx.computeOnClient(mc -> Tabs.row());
            List<String> drawn = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerTabRows.rowForChecks(
                    com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP));
            if (!drawn.equals(row)) fail("MenuKit draws the row " + drawn + ", not " + row);
            String wasShown = ctx.computeOnClient(mc -> Tabs.shown());
            ctx.getInput().setCursorPos(5, 5);
            ctx.waitTick();
            ctx.getInput().scroll(-1);
            ctx.waitTicks(2);
            int was = row.indexOf(wasShown);
            int now = row.indexOf(ctx.computeOnClient(mc -> Tabs.shown()));
            if (now != Math.min(was + 1, row.size() - 1)) fail("a turn down moved the tab from " + was + " to " + now);
            // Over a box's grid, which doesn't scroll, the wheel moves tabs too.
            double[] mid = ctx.computeOnClient(mc -> {
                var box = ContainerRegion.CONTAINERS.box(mc.gui.screen());
                double f = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                return new double[] {(box.leftPos() + box.imageWidth() / 2.0) * f, (box.topPos() + box.imageHeight() / 2.0) * f};
            });
            ctx.getInput().setCursorPos(mid[0], mid[1]);
            ctx.waitTick();
            ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            String up = ctx.computeOnClient(mc -> Tabs.shown());
            if (!wasShown.equals(up)) fail("a turn up didn't go back to " + wasShown + ": " + up);

            // A bundle of 60 stacks (61 slots, seven rows) grows to the room there is, then
            // its grid scrolls: the panel stops short of the double chest's height.
            open(s -> isChest(s) && idOf(s).equals("bundle"), "the full bundle");
            int bundleHeight = height();
            if (bundleHeight > chestHeight) fail("the full bundle's panel (" + bundleHeight + ") is taller than the double chest's (" + chestHeight + ")");
            if (bundleHeight <= boxHeight) fail("the full bundle's panel (" + bundleHeight + ") didn't grow past a box's (" + boxHeight + ")");
            ctx.takeScreenshot("tabs-full-bundle");

            // A real click on the chest's tab (the first), with the mouse.
            double[] tab = ctx.computeOnClient(mc -> {
                var g = com.trevlar.menukit.inject.ContainerRegions.geometry(mc.gui.screen());
                var w = mc.getWindow();
                double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
                var line = com.trevlar.menukit.inject.ContainerTabRows.line(g, com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP);
                return new double[] {(line.x() + 13) * f, (line.rowTop() + 10) * f};
            });
            ctx.getInput().setCursorPos(tab[0], tab[1]);
            ctx.waitTick();
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
            if (!shown(CHEST)) fail("clicking the chest's tab didn't show the chest");
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            if (height() != chestHeight) fail("back on the chest, the panel is " + height() + " tall, not " + chestHeight);
            ctx.takeScreenshot("tabs-double-chest");
            ctx.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
        }
        if (!failures.isEmpty()) throw new AssertionError("tab checks failed:\n- " + String.join("\n- ", failures));
    }

    private boolean shown(String sectionId) {
        return ctx.computeOnClient(mc -> ContainerRegion.anchor(sectionId).box(mc.gui.screen()) != null);
    }

    /** The container panel's height this frame. */
    private int height() {
        return ctx.computeOnClient(mc -> {
            var box = ContainerRegion.CONTAINERS.box(mc.gui.screen());
            return box == null ? -1 : box.imageHeight();
        });
    }

    private static boolean isChest(Slot s) {
        return !(s.container instanceof Inventory) && com.trevlar.slotkit.api.slot.CreatedSlot.of(s) == null;
    }

    private static String idOf(Slot s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath();
    }

    private void fail(String what) { failures.add(what); }

    /**
     * A double-click on the first shown slot matching {@code p}, picking the chest's tab first
     * when it isn't shown (what was just opened is).
     */
    private void open(Predicate<Slot> p, String what) {
        Predicate<AbstractContainerScreen<?>> has = screen -> {
            for (Slot s : screen.getMenu().slots) if (s.isActive() && p.test(s)) return true;
            return false;
        };
        if (!ctx.computeOnClient(mc -> has.test((AbstractContainerScreen<?>) mc.gui.screen()))) {
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
        }
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
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int i = 0; i < 2; i++) for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        });
        ctx.waitTicks(6);
    }
}
