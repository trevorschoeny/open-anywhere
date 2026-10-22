package com.trevorschoeny.openanywhere.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.inject.ContainerRegions;
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
 * layout, checked for what it shows and screenshotted for the eye.
 */
public class LayoutChecks implements FabricClientGameTest {

    private static final int SDL_ALT = 0x100;

    private ClientGameTestContext ctx;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulkers-in-inventory", "layouts")) {
            // Scale 3 (640 x 360 at 1920 x 1080): short enough that the list scrolls.
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
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen c && c.getMenu().getRowCount() == 6)) throw new AssertionError("the double chest didn't open");

            // Opened in Tabs, where each opened container shows alone, so every slot is in view:
            // a new one is picked as it opens, and the chest's tab is picked again for the next.
            LayoutPrototype.layout = Layout.TABS;
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 1, "the ender chest");
            open(s -> Groups.isIn(s, Openable.ENDER_CHEST) && Openable.of(s.getItem()) == Openable.SHULKER, "the green box in the ender chest");
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(0));
            ctx.waitTicks(2);
            open(s -> isChest(s) && idOf(s).equals("red_shulker_box"), "the red box");
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(0));
            ctx.waitTicks(2);
            open(s -> isChest(s) && idOf(s).equals("orange_shulker_box"), "the orange box");
            open(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box");
            LayoutPrototype.layout = Layout.CONTENTS;
            ctx.waitTicks(2);
            int open = scene.world().getServer().computeOnServer(sv -> Sessions.of(sv.getPlayerList().getPlayers().getFirst()).size());
            if (open != 5) fail(open + " things open, not 5");
            List<String> ids = ctx.computeOnClient(mc -> LayoutPrototype.idsForChecks());
            if (ids.size() != 6) throw new AssertionError(ids.size() + " containers listed, not 6: " + ids + "; " + failures);

            // Contents: the last entry glides the list to the end and marks it; the first back to the top.
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(ids.size() - 1));
            ctx.waitTicks(20);
            if (!shown(ids.getLast())) fail("the last entry didn't scroll to its section");
            String marked = ctx.computeOnClient(mc -> LayoutPrototype.currentForChecks());
            if (!ids.getLast().equals(marked)) fail("after the glide to the last section, the contents mark " + marked);
            ctx.takeScreenshot("layouts-2-contents-end");
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(0));
            ctx.waitTicks(20);
            double top = ctx.computeOnClient(mc -> ContainerRegions.scroll());
            ctx.waitTicks(5);
            double still = ctx.computeOnClient(mc -> ContainerRegions.scroll());
            if (top != 0 || still != 0) fail("the first entry left the scroll at " + top + ", then " + still + ", not 0");
            marked = ctx.computeOnClient(mc -> LayoutPrototype.currentForChecks());
            if (!ids.getFirst().equals(marked)) fail("at the top, the contents mark " + marked);
            ctx.takeScreenshot("layouts-2-contents-top");

            // Tabs: one container at a time.
            LayoutPrototype.layout = Layout.TABS;
            for (int i : new int[] {0, 2, 4}) {
                ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(i));
                ctx.waitTicks(3);
                int n = 0;
                for (String id : ids) if (shown(id)) n++;
                if (n != 1 || !shown(ids.get(i))) fail("tab " + i + " shows " + n + " sections, not just its own");
                // The chest's own slots go with its tab: none active while another tab is picked.
                boolean chestSlots = ctx.computeOnClient(mc -> {
                    for (Slot s : ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots) if (isChest(s) && s.isActive()) return true;
                    return false;
                });
                if (chestSlots != (i == 0)) fail("tab " + i + ": the chest's slots are " + (chestSlots ? "active" : "inactive"));
            }
            ctx.takeScreenshot("layouts-1-tabs-box");
            ctx.runOnClient(mc -> LayoutPrototype.clickForChecks(0));
            ctx.waitTicks(3);
            ctx.takeScreenshot("layouts-1-tabs-double-chest");
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

    private static String idOf(Slot s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath();
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
