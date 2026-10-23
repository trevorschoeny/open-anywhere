package com.trevorschoeny.openanywhere.checks;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.openanywhere.ContainerHost;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.api.OpenItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import testgallery.SceneWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Checks for the modes scene (test-gallery/scenes/open-anywhere/modes.json): opening
 * in survival and creative, a mode switch with an item open, and the settings. Every check
 * opens the way a hand does, double-click through the mouse.
 */
public class ModeChecks implements FabricClientGameTest {

    private static final String SHULKER = "minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:stone\",count:32}}]]";
    private static final String BUNDLE = "minecraft:bundle[bundle_contents=[{id:\"minecraft:stone\",count:4}]]";

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "open-anywhere", "modes")) {
            this.scene = scene;
            run("openanywhere:survival_shulker", this::survivalShulker);
            run("openanywhere:survival_ender", this::survivalEnder);
            run("openanywhere:creative_inventory_tab", this::creativeInventoryTab);
            run("openanywhere:creative_item_tab", this::creativeItemTab);
            run("openanywhere:settings", this::settings);
        }
        if (!failures.isEmpty()) throw new AssertionError("mode checks failed:\n- " + String.join("\n- ", failures));
    }

    /** One station from a clean survival player holding a box, an ender chest and a bundle. */
    private void run(String id, Runnable check) {
        JsonObject station = scene.station(id);
        try {
            scene.standAt(station, 30);
            for (String c : new String[] {"gamemode survival @p", "clear @p",
                    "item replace entity @p hotbar.0 with " + SHULKER,
                    "item replace entity @p hotbar.1 with minecraft:ender_chest",
                    "item replace entity @p hotbar.2 with " + BUNDLE}) command(c);
            ctx.waitTicks(3);
            check.run();
        } catch (AssertionError e) {
            failures.add(id + ": " + e.getMessage());
        } finally {
            ctx.takeScreenshot("modes-" + id.substring(id.indexOf(':') + 1));
            ctx.runOnClient(mc -> { if (mc.player != null) mc.player.closeContainer(); });
            // A stack left on the cursor (vanilla's creative pick-up) would block the next open.
            ctx.runOnClient(mc -> { if (mc.player != null) mc.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY); });
            scene.world().getServer().runOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY));
            ctx.waitTicks(3);
        }
    }

    // Stations

    /**
     * From the inventory a box opens on vanilla's shulker box screen, as a tab; a bundle
     * opened from there is a second tab, the box's still on the row.
     */
    private void survivalShulker() {
        openInventory();
        doubleClickNow(hotbar(0), "shulker box");
        frames("anim-shulker-open");
        expectScreen(ShulkerBoxScreen.class, "double-click on the box");
        expectServerSession(Openable.SHULKER, true);
        doubleClick(hotbar(2), "the bundle, from the box's screen");
        int tabs = ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.Tabs.row().size());
        if (tabs != 3) throw new AssertionError("a box and a bundle opened from the inventory: " + tabs + " tabs, not 3");
        if (!ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.ContainerHost.is(((AbstractContainerScreen<?>) mc.gui.screen()).getMenu())))
            throw new AssertionError("the bundle didn't open on its host screen");
    }

    private void survivalEnder() {
        openInventory();
        doubleClickNow(hotbar(1), "ender chest");
        frames("anim-ender-open");
        expectScreen(ContainerScreen.class, "double-click on the ender chest");
        expectServerSession(Openable.ENDER_CHEST, true);
        // Again: it closes, back to the inventory, and its lid shuts.
        doubleClickNow(hotbar(1), "the open ender chest");
        frames("anim-ender-close");
        expectServerSession(Openable.ENDER_CHEST, false);
    }

    /**
     * Screenshots as fast as they come, named by the milliseconds since the click, for as
     * long as the open animation runs and a little after: the animation, for a look,
     * stitched into a WebP. Only with {@code -Popenanywhere.capture}, which slows the
     * animation so the screenshots keep up; otherwise the double-click's usual wait.
     */
    private void frames(String name) {
        int millis = Integer.getInteger("openanywhere.openMillis", 0);
        long start = System.currentTimeMillis();
        while (millis > 0 && System.currentTimeMillis() - start < millis + 600)
            ctx.takeScreenshot(name + "-" + String.format("%05d", System.currentTimeMillis() - start));
        ctx.waitTicks(6);
    }

    /**
     * Creative's inventory tab: a double-click opens the box as if the player had opened the
     * container itself, so its own screen replaces creative's and the
     * creative tabs are gone. The box goes back in its slot, one box, none copied.
     */
    private void creativeInventoryTab() {
        command("gamemode creative @p");
        openCreative(CreativeModeTab.Type.INVENTORY);
        doubleClick(item(Items.SHULKER_BOX), "shulker box on the inventory tab");
        expectScreen(ShulkerBoxScreen.class, "double-click on the box in creative's inventory tab");
        expectServerSession(Openable.SHULKER, true);
        int boxes = scene.world().getServer().computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.SHULKER_BOX));
        if (boxes != 1) throw new AssertionError(boxes + " shulker boxes in the inventory after opening in creative, not 1");
        // The Inventory tab goes back to creative's own inventory, as the inventory key does in creative.
        ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Tabs.pick(com.trevorschoeny.openanywhere.TabId.INVENTORY));
        expectScreen(CreativeModeInventoryScreen.class, "the Inventory tab in creative");
    }

    /** An item tab shows only the hotbar; nothing opens from it and no bundle slots appear. */
    private void creativeItemTab() {
        command("gamemode creative @p");
        openCreative(CreativeModeTab.Type.CATEGORY);
        doubleClick(item(Items.SHULKER_BOX), "shulker box on an item tab");
        // Vanilla's right-click picks the box up: still one box, on the cursor.
        nothingOpened(1);
    }

    /**
     * The player's settings: a kind turned off doesn't open, double-click
     * turned off doesn't open, and an item opened before its kind went off still closes.
     * Every setting goes back on at the end, pass or fail.
     */
    private void settings() {
        try {
            openInventory();
            doubleClick(hotbar(2), "bundle");
            expectServerSession(Openable.BUNDLE, true);
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.setOpens(Openable.BUNDLE, false));
            doubleClick(hotbar(2), "the open bundle, bundles now off");
            expectServerSession(Openable.BUNDLE, false);
            // Its tab went with it: back to the inventory.
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
            openInventory();

            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.setOpens(Openable.SHULKER, false));
            doubleClick(hotbar(0), "shulker box, shulker boxes off");
            expectServerSession(Openable.SHULKER, false);
            // Vanilla's double-click left the box on the cursor; it goes, so the next one starts clean.
            clearCursor();

            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.reset());
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.setDoubleClick(false));
            doubleClick(hotbar(1), "ender chest, double-click off");
            expectServerSession(Openable.ENDER_CHEST, false);
        } finally {
            ctx.runOnClient(mc -> com.trevorschoeny.openanywhere.Settings.reset());
        }
    }

    // Helpers

    private void clearCursor() {
        ctx.runOnClient(mc -> mc.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY));
        scene.world().getServer().runOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY));
        ctx.waitTicks(3);
    }

    private void command(String c) {
        scene.world().getServer().runCommand(c);
    }

    private void openInventory() {
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(3);
    }

    private void openCreative(CreativeModeTab.Type type) {
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof CreativeModeInventoryScreen screen))
                throw new AssertionError("the inventory key opened " + mc.gui.screen() + " in creative");
            ((FabricCreativeModeInventoryScreen) screen).setSelectedTab(CreativeModeTabs.allTabs().stream()
                    .filter(t -> t.getType() == type).findFirst().orElseThrow());
        });
        ctx.waitTicks(3);
    }

    /** The player's hotbar slot, shown, on a survival screen. */
    private static Predicate<Slot> hotbar(int i) {
        return s -> s.container instanceof Inventory && s.getContainerSlot() == i && s.isActive();
    }

    /** The shown slot holding this item (creative's wrappers carry no usable index). */
    private static Predicate<Slot> item(net.minecraft.world.item.Item item) {
        return s -> s.isActive() && s.getItem().is(item);
    }

    /** Still creative's screen, no session on the server, and {@code boxes} boxes after vanilla's own click. */
    private void nothingOpened(int boxes) {
        ctx.waitTicks(3);
        String screen = ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen()));
        if (!screen.contains("CreativeModeInventoryScreen")) throw new AssertionError("the screen changed to " + screen);
        expectServerSession(Openable.SHULKER, false);
        int found = ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.SHULKER_BOX)
                + (mc.player.containerMenu.getCarried().is(Items.SHULKER_BOX) ? mc.player.containerMenu.getCarried().getCount() : 0));
        if (found != boxes) throw new AssertionError("expected " + boxes + " shulker box(es) after vanilla's click, found " + found);
    }

    private void expectScreen(Class<?> type, String after) {
        ctx.waitTicks(3);
        Object screen = ctx.computeOnClient(mc -> mc.gui.screen());
        if (!type.isInstance(screen)) throw new AssertionError(after + " opened " + screen + ", not a " + type.getSimpleName());
    }

    /** Whether the server has an item of {@code kind} in front for the player, as other mods read it. */
    private void expectServerSession(Openable kind, boolean open) {
        ctx.waitTicks(3);
        boolean isOpen = scene.world().getServer().computeOnServer(s -> {
            var shown = OpenItems.shown(s.getPlayerList().getPlayers().getFirst());
            return shown != null && shown.kind() == kind;
        });
        if (isOpen != open) throw new AssertionError("server " + (open ? "has no " : "still has a ") + kind + " in front");
    }

    private void hover(Predicate<Slot> p, String what) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) throw new AssertionError("no container screen for " + what);
            Slot found = null;
            for (Slot s : screen.getMenu().slots) if (p.test(s)) { found = s; break; }
            if (found == null) throw new AssertionError("no " + what + " on " + screen);
            var edge = (com.trevorschoeny.openanywhere.mixin.ScreenAccessor) screen;
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(edge.openanywhere$leftPos() + found.x + 8) * f, (edge.openanywhere$topPos() + found.y + 8) * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
    }

    private void click(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    /**
     * A double-click, as the real mouse sends it: two left presses on the slot, inside
     * vanilla's double-click window (one client task, so no frame or tick falls between).
     * TestInput clicks carry no modifiers, so this goes through the MouseHandler hook.
     */
    private void doubleClick(Predicate<Slot> p, String what) {
        doubleClickNow(p, what);
        ctx.waitTicks(6);
    }

    /** The double-click alone, with no wait after: for catching what follows frame by frame. */
    private void doubleClickNow(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int i = 0; i < 2; i++) for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        });
    }

}
