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
 * Tabs on a double chest (scene open-anywhere/tabs): the ender chest, a box inside
 * it, two boxes from the chest and one from the hotbar, each shown on its own as it opens,
 * then the chest again. Checked for what shows and how tall the panel is, and screenshotted
 * at GUI scale 2 (240 px tall, the size it's played at).
 */
public class TabChecks implements FabricClientGameTest {

    private static final String CHEST = "openanywhere:chest";

    private ClientGameTestContext ctx;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "open-anywhere", "tabs")) {
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
            if (!shown("openanywhere:opened#ender_chest_0")) fail("the ender chest wasn't shown as it opened");
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            int chestHeight = height();
            ctx.runOnClient(mc -> Tabs.pick("openanywhere:opened#ender_chest_0"));
            ctx.waitTicks(3);
            open(s -> Groups.isIn(s, Openable.ENDER_CHEST) && Openable.of(s.getItem()) == Openable.SHULKER, "the green box in the ender chest");
            if (!shown("openanywhere:opened#shulker_0")) fail("the green box wasn't shown as it opened");
            int boxHeight = height();
            if (boxHeight >= chestHeight) fail("a box's panel (" + boxHeight + ") isn't shorter than the double chest's (" + chestHeight + ")");
            ctx.takeScreenshot("tabs-box");
            // A box's tab shown: the screen must not be laid out again frame after frame.
            int before = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerRegions.relayouts());
            ctx.waitTicks(20);
            int after = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerRegions.relayouts());
            if (after - before > 2) fail("with a box's tab shown, the screen was laid out again " + (after - before) + " times in 20 ticks");

            // Every frame from the double-click on looks either as before it (the chest shown,
            // the red box not greyed) or as after (the box's grid drawn at its height, the box
            // greyed in the chest): never a frame between, which is the flash (a frame of the
            // panel 8 px tall, laid out before the new grid was sized).
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            List<String> frames = java.util.Collections.synchronizedList(new ArrayList<>());
            boolean[] recording = {true};
            // Attached again on every init: a screen's own events are cleared when it inits.
            java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> record = sc ->
                    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(sc).register((screen, g, mx, my, delta) -> {
                        if (!recording[0]) return;
                        Slot red = null;
                        for (Slot sl : ((AbstractContainerScreen<?>) screen).getMenu().slots)
                            if (isChest(sl) && idOf(sl).equals("red_shulker_box")) { red = sl; break; }
                        boolean grey = red != null && Sessions.isOpenOnClient(red.container, red.getContainerSlot())
                                && !com.trevorschoeny.openanywhere.ClientMarks.awaitingTabForChecks(red);
                        String shownNow = Tabs.shown();
                        frames.add(shownNow + " drawn=" + (shownNow != null && Tabs.drawnForChecks(shownNow))
                                + " chestDrawn=" + Tabs.drawnForChecks(CHEST) + " grey=" + grey
                                + " h=" + (ContainerRegion.CONTAINERS.box(screen) == null ? -1 : ContainerRegion.CONTAINERS.box(screen).imageHeight()));
                    });
            ctx.runOnClient(mc -> {
                record.accept(mc.gui.screen());
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
                    if (!recording[0] || !(screen instanceof ContainerScreen)) return;
                    frames.add("INIT");
                    record.accept(screen);
                });
            });
            open(s -> isChest(s) && idOf(s).equals("red_shulker_box"), "the red box");
            recording[0] = false;
            List<String> seen = List.copyOf(frames);
            // After the open, every frame is the box at its own height, the last frame's.
            String settled = seen.getLast().substring(seen.getLast().indexOf(" h="));
            boolean unchanged = true;
            for (String f : seen) {
                if (f.equals("INIT")) continue;
                boolean old = f.startsWith(CHEST + " ") && f.contains("chestDrawn=true") && f.contains("grey=false");
                boolean now = !f.startsWith(CHEST + " ") && f.contains(" drawn=true") && f.contains("grey=true") && f.endsWith(settled);
                if (unchanged && old) continue;
                unchanged = false;
                if (!now) { fail("a frame between the chest and the opened box: " + String.join(" | ", seen)); break; }
            }
            open(s -> isChest(s) && idOf(s).equals("orange_shulker_box"), "the orange box");
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box");
            int open = scene.world().getServer().computeOnServer(sv -> Sessions.of(sv.getPlayerList().getPlayers().getFirst()).size());
            if (open != 5) fail(open + " things open, not 5");
            // Back on the chest after two more opened, the red box's slot is still greyed.
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Tabs.pick(CHEST));
            ctx.waitTicks(3);
            String red = ctx.computeOnClient(mc -> {
                for (Slot sl : ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots) {
                    if (!isChest(sl) || !idOf(sl).equals("red_shulker_box")) continue;
                    return "open=" + Sessions.isOpenOnClient(sl.container, sl.getContainerSlot())
                            + " awaiting=" + com.trevorschoeny.openanywhere.ClientMarks.awaitingTabForChecks(sl);
                }
                return "no red box";
            });
            if (!red.equals("open=true awaiting=false")) fail("back on the chest, the red box isn't greyed: " + red);
            // Closed and opened again, the red box's tab goes to the end of the row.
            open(sl -> isChest(sl) && idOf(sl).equals("red_shulker_box"), "the open red box, to close it");
            open(sl -> isChest(sl) && idOf(sl).equals("red_shulker_box"), "the red box, again");
            List<String> order = ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.Tabs.row());
            String redTab = ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.Tabs.shown());
            if (!order.getLast().equals(redTab)) fail("the red box opened again isn't the last tab: " + order + ", shown " + redTab);
            // The one-container rule: with 5 things open at once, still only one section's slots show.
            List<String> present = ctx.computeOnClient(mc -> Tabs.row());
            int visibleSections = ctx.computeOnClient(mc -> {
                int count = 0;
                for (String id : present) if (Tabs.drawnForChecks(id)) count++;
                return count;
            });
            if (visibleSections != 1)
                fail("with " + present.size() + " present, " + visibleSections + " sections are shown at once, not 1");

            // The wheel away from the list moves a tab: down to the right, up to the left.
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            List<String> row = ctx.computeOnClient(mc -> Tabs.row());
            List<String> drawn = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerTabRows.rowForChecks(
                    com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP));
            // MenuKit draws one tab per section, in the row's order: the chest, then in opening order.
            if (!drawn.equals(row)) fail("MenuKit draws the tabs " + drawn + ", not " + row);
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
            // With the wheel turned off in the settings, a turn leaves the tab alone.
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.setWheel(false));
            ctx.getInput().scroll(-1);
            ctx.waitTicks(2);
            String still = ctx.computeOnClient(mc -> Tabs.shown());
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.setWheel(true));
            if (!wasShown.equals(still)) fail("with the wheel off, a turn moved the tab to " + still);

            // A bundle of 60 stacks (61 slots, seven rows) grows to the room there is, then
            // its grid scrolls: the panel stops short of the double chest's height.
            open(s -> isChest(s) && idOf(s).equals("bundle"), "the full bundle");
            int bundleHeight = height();
            if (bundleHeight > chestHeight) fail("the full bundle's panel (" + bundleHeight + ") is taller than the double chest's (" + chestHeight + ")");
            if (bundleHeight <= boxHeight) fail("the full bundle's panel (" + bundleHeight + ") didn't grow past a box's (" + boxHeight + ")");
            ctx.takeScreenshot("tabs-full-bundle");

            // A real click, with the mouse, on the tab after the first one in view: it shows.
            var edge = com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP;
            int first = ctx.computeOnClient(mc -> com.trevlar.menukit.inject.ContainerTabRows.firstForChecks(edge));
            double[] tab = ctx.computeOnClient(mc -> {
                var g = com.trevlar.menukit.inject.ContainerRegions.geometry(mc.gui.screen());
                double f = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                var line = com.trevlar.menukit.inject.ContainerTabRows.line(g, edge);
                var fit = com.trevlar.menukit.inject.ContainerTabRows.fit(line.width(),
                        com.trevlar.menukit.inject.ContainerTabRows.rowForChecks(edge).size(), first);
                return new double[] {(com.trevlar.menukit.inject.ContainerTabRows.tabX(line.x(), fit, first + 1) + 13) * f, (line.rowTop() + 10) * f};
            });
            String target = ctx.computeOnClient(mc -> Tabs.row()).get(first + 1);
            ctx.getInput().setCursorPos(tab[0], tab[1]);
            ctx.waitTick();
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
            if (!target.equals(ctx.computeOnClient(mc -> Tabs.shown()))) fail("clicking the tab for " + target + " didn't show it");
            ctx.runOnClient(mc -> Tabs.pick(CHEST));
            ctx.waitTicks(3);
            if (height() != chestHeight) fail("back on the chest, the panel is " + height() + " tall, not " + chestHeight);
            ctx.takeScreenshot("tabs-double-chest");
            ctx.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
        }
        if (!failures.isEmpty()) throw new AssertionError("tab checks failed:\n- " + String.join("\n- ", failures));
    }

    private boolean shown(String sectionId) {
        return ctx.computeOnClient(mc -> Tabs.drawnForChecks(sectionId));
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
