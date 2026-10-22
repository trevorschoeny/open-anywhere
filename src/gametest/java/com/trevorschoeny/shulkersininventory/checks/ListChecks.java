package com.trevorschoeny.shulkersininventory.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.slotkit.api.slot.ClientSlots;
import com.trevorschoeny.shulkersininventory.Groups;
import com.trevorschoeny.shulkersininventory.Openable;
import com.trevorschoeny.shulkersininventory.Session;
import com.trevorschoeny.shulkersininventory.Sessions;
import com.trevorschoeny.shulkersininventory.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import testgallery.SceneWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Checks for the container-list scene (test-gallery/scenes/shulkers-in-inventory/container-list.json):
 * on a chest's screen a box or bundle opens below the chest, in MenuKit's container list,
 * and the chest stays open. Every open is a real shift+right-click through the mouse.
 */
public class ListChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "shulkers-in-inventory", "container-list")) {
            this.scene = scene;
            run("shulkersininventory:list_box_in_chest", this::boxInChest);
            run("shulkersininventory:list_shift_click", this::shiftClick);
            run("shulkersininventory:list_box_from_hotbar", this::boxFromHotbar);
            run("shulkersininventory:list_ender_box", this::enderBox);
            run("shulkersininventory:list_bundle", this::bundle);
            run("shulkersininventory:list_nested", this::nestedRefused);
            run("shulkersininventory:list_ender_section", this::enderSection);
            run("shulkersininventory:list_ender_section_box", this::enderSectionBox);
            run("shulkersininventory:list_ender_at_ender_chest", this::enderAtEnderChest);
        }
        if (!failures.isEmpty()) throw new AssertionError("container list checks failed:\n- " + String.join("\n- ", failures));
    }

    private JsonObject station;

    private void run(String id, Runnable check) {
        station = scene.station(id);
        try {
            check.run();
        } catch (AssertionError e) {
            failures.add(id + ": " + e.getMessage());
        } finally {
            ctx.takeScreenshot("list-" + id.substring(id.indexOf(':') + 1));
            ctx.runOnClient(mc -> { if (mc.player != null) mc.player.closeContainer(); });
            ctx.waitTicks(3);
        }
    }

    // ── Stations ───────────────────────────────────────────────────────────

    private void boxInChest() {
        openBlock();
        shiftRightClick(chestSlot(4), "the purple box in the chest");
        expectStacked(Openable.SHULKER);
        expectChestSection();
        // The box's own slot is greyed and locked: a click doesn't pick it up.
        click(chestSlot(4), "the opened box");
        if (carried() != 0) throw new AssertionError("the opened box was picked up");
        // Its contents are the box's: 5 diamonds in its first slot.
        int diamonds = ctx.computeOnClient(mc -> groupSlot(screen(mc), 0).getItem().is(Items.DIAMOND)
                ? groupSlot(screen(mc), 0).getItem().getCount() : 0);
        if (diamonds != 5) throw new AssertionError("the box's first slot shows " + diamonds + " diamonds, not 5");
    }

    private void shiftClick() {
        command("item replace entity @p inventory.0 with minecraft:dirt 64");
        openBlock();
        shiftRightClick(chestSlot(4), "the purple box in the chest");
        expectStacked(Openable.SHULKER);
        // In: from the inventory into the box first, not the chest.
        shiftLeftClick(s -> s.container instanceof Inventory && s.getItem().is(Items.DIRT), "the dirt");
        int inBox = boxCount(Items.DIRT);
        if (inBox != 64) throw new AssertionError("shift-click from the inventory put " + inBox + " dirt in the box, not 64");
        // Out: from the box back into the chest that holds it.
        shiftLeftClick(s -> groupIndex(s) >= 0 && s.getItem().is(Items.DIRT), "the dirt in the box");
        int inChest = scene.world().getServer().computeOnServer(server -> {
            Container chest = (ChestBlockEntity) server.overworld().getBlockEntity(block());
            int n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.DIRT)) n += chest.getItem(i).getCount();
            return n;
        });
        if (inChest != 64) throw new AssertionError("shift-click out of the box put " + inChest + " dirt in the chest, not 64");
        if (boxCount(Items.DIRT) != 0) throw new AssertionError("dirt left in the box after shift-clicking it out");
    }

    private void boxFromHotbar() {
        command("item replace entity @p hotbar.0 with minecraft:lime_shulker_box[container=[{slot:0,item:{id:\"minecraft:emerald\",count:3}}]]");
        openBlock();
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box in the hotbar");
        expectStacked(Openable.SHULKER);
        expectChestSection();
        // Again on the same box: it closes, the barrel's screen stays.
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 0, "the lime box again");
        expectServerSession(Openable.SHULKER, false);
        if (ctx.computeOnClient(mc -> ClientSlots.isShown(Groups.shulker().id())))
            throw new AssertionError("the box's slots still show after closing it");
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen))
            throw new AssertionError("closing the box closed the barrel's screen too");
    }

    private void enderBox() {
        openBlock();
        shiftRightClick(s -> !(s.container instanceof Inventory) && groupIndex(s) < 0 && s.getContainerSlot() == 4,
                "the green box in the ender chest");
        expectStacked(Openable.SHULKER);
        expectChestSection();
    }

    private void bundle() {
        command("item replace entity @p hotbar.1 with minecraft:bundle[bundle_contents=[{id:\"minecraft:stone\",count:4},{id:\"minecraft:granite\",count:2}]]");
        openBlock();
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 1, "the bundle");
        expectStacked(Openable.BUNDLE);
        expectChestSection();
    }

    /**
     * Station "list_nested": shift+right-click on a bundle inside an opened box does nothing.
     * The box stays open, no bundle opens, and dirt shift-clicked in afterwards goes into the
     * box, as it would with no bundle there.
     */
    private void nestedRefused() {
        command("item replace entity @p inventory.0 with minecraft:dirt 16");
        openBlock();
        shiftRightClick(chestSlot(4), "the cyan box in the chest");
        expectStacked(Openable.SHULKER);
        shiftRightClick(s -> groupIndex(s) == 0 && s.getItem().is(Items.BUNDLE), "the bundle inside the box");
        expectServerSession(Openable.BUNDLE, false);
        expectStacked(Openable.SHULKER);
        shiftLeftClick(s -> s.container instanceof Inventory && s.getItem().is(Items.DIRT), "the dirt");
        int inBox = boxCount(Items.DIRT);
        if (inBox != 16) throw new AssertionError("after the refused bundle, shift-click put " + inBox + " dirt in the box, not 16");
    }

    /**
     * Station "list_ender_section": the ender chest item on a chest's screen opens your ender
     * chest as a section below the chest, and dirt shift-clicked in lands in your real ender
     * chest. Again on the item: it closes, the chest stays.
     */
    private void enderSection() {
        command("item replace entity @p hotbar.2 with minecraft:ender_chest");
        command("item replace entity @p inventory.0 with minecraft:dirt 16");
        int before = enderDirt();
        openBlock();
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 2, "the ender chest in the hotbar");
        expectStacked(Openable.ENDER_CHEST);
        expectChestSection();
        shiftLeftClick(s -> s.container instanceof Inventory && s.getItem().is(Items.DIRT), "the dirt");
        if (enderDirt() != before + 16) throw new AssertionError("shift-click put " + (enderDirt() - before) + " dirt in the ender chest, not 16");
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 2, "the open ender chest");
        expectServerSession(Openable.ENDER_CHEST, false);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen))
            throw new AssertionError("closing the ender chest closed the chest's screen too");
    }

    /**
     * Station "list_ender_section_box": the green box inside the ender chest's section opens
     * below it with the ender chest still open (design, Rule 2), shift-clicking out of the
     * box goes back into the ender chest, and closing the ender chest closes the box.
     */
    private void enderSectionBox() {
        command("item replace entity @p hotbar.2 with minecraft:ender_chest");
        command("item replace entity @p enderchest.4 with minecraft:green_shulker_box[container=[{slot:0,item:{id:\"minecraft:dirt\",count:5}}]]");
        int before = enderDirt();
        openBlock();
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 2, "the ender chest in the hotbar");
        expectStacked(Openable.ENDER_CHEST);
        shiftRightClick(s -> Groups.isIn(s, Openable.ENDER_CHEST) && Openable.of(s.getItem()) == Openable.SHULKER, "the green box in the ender chest");
        expectStacked(Openable.SHULKER);
        expectServerSession(Openable.ENDER_CHEST, true);
        ctx.takeScreenshot("list-ender-and-box-open");
        shiftLeftClick(s -> Groups.isIn(s, Openable.SHULKER) && s.getItem().is(Items.DIRT), "the dirt in the box");
        if (enderDirt() != before + 5) throw new AssertionError("shift-click out of the box put " + (enderDirt() - before) + " dirt in the ender chest, not 5");
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.trevorschoeny.shulkersininventory.CloseC2S(Openable.ENDER_CHEST)));
        ctx.waitTicks(5);
        expectServerSession(Openable.ENDER_CHEST, false);
        expectServerSession(Openable.SHULKER, false);
    }

    /** Station "list_ender_at_ender_chest": at a placed ender chest the item adds nothing. */
    private void enderAtEnderChest() {
        command("item replace entity @p hotbar.2 with minecraft:ender_chest");
        openBlock();
        shiftRightClick(s -> s.container instanceof Inventory && s.getContainerSlot() == 2, "the ender chest in the hotbar");
        expectServerSession(Openable.ENDER_CHEST, false);
    }

    /** Dirt in the player's real ender chest, read on the server. */
    private int enderDirt() {
        return scene.world().getServer().computeOnServer(server -> {
            var ender = player(server).getEnderChestInventory();
            int n = 0;
            for (int i = 0; i < ender.getContainerSize(); i++) if (ender.getItem(i).is(Items.DIRT)) n += ender.getItem(i).getCount();
            return n;
        });
    }

    // ── Expectations ───────────────────────────────────────────────────────

    /** A server session below the chest, its slots shown, and still the chest's screen. */
    private void expectStacked(Openable kind) {
        Session s = scene.world().getServer().computeOnServer(server -> Sessions.get(player(server), kind));
        if (s == null) throw new AssertionError("no " + kind + " session on the server");
        if (!s.stacked()) throw new AssertionError("the " + kind + " opened its own screen, not below the chest");
        if (!ctx.computeOnClient(mc -> ClientSlots.isShown(Groups.of(kind).id())))
            throw new AssertionError("the " + kind + "'s slots don't show");
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen))
            throw new AssertionError("the chest's screen didn't stay open");
    }

    /** The chest's own slots still drawn: its first slot is on screen, inside the screen's bounds. */
    private void expectChestSection() {
        boolean inside = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = screen(mc);
            ScreenAccessor b = (ScreenAccessor) screen;
            Slot first = screen.getMenu().getSlot(0);
            int top = b.shulkersininventory$topPos();
            return first.isActive() && first.y >= 0 && top + first.y + 16 <= screen.height;
        });
        if (!inside) throw new AssertionError("the chest's own grid isn't shown above the opened item");
    }

    private void expectServerSession(Openable kind, boolean open) {
        boolean isOpen = scene.world().getServer().computeOnServer(s -> Sessions.get(player(s), kind) != null);
        if (isOpen != open) throw new AssertionError("server " + (open ? "has no " : "still has a ") + kind + " session");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static AbstractContainerScreen<?> screen(net.minecraft.client.Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) throw new AssertionError("no container screen open");
        return screen;
    }

    /**
     * The station's container block. Every checked station puts it at {@code [2, 0, 2]} from its
     * origin (stations.json carries the origin, not the blocks).
     */
    private BlockPos block() {
        JsonArray o = station.getAsJsonArray("origin");
        return new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
    }

    /** Stands two blocks north of the station's container and opens it. */
    private void openBlock() {
        BlockPos b = block();
        command(String.format("tp @p %.1f %d %.1f 0 30", b.getX() + 0.5, b.getY(), b.getZ() - 1.5));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(b);
        ctx.waitTick();
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen))
            throw new AssertionError("the container didn't open");
    }

    private void command(String c) {
        scene.world().getServer().runCommand(c);
    }

    /** The open container's own slot {@code i} (not the player's, not this mod's). */
    private static Predicate<Slot> chestSlot(int i) {
        return s -> !(s.container instanceof Inventory) && groupIndex(s) < 0 && s.getContainerSlot() == i;
    }

    /** A slot's index in this mod's shulker or bundle group, or -1. */
    private static int groupIndex(Slot s) {
        var created = com.trevlar.slotkit.api.slot.CreatedSlot.of(s);
        return created == null ? -1 : created.getLocalIndex();
    }

    private static Slot groupSlot(AbstractContainerScreen<?> screen, int i) {
        for (Slot s : screen.getMenu().slots) {
            var created = com.trevlar.slotkit.api.slot.CreatedSlot.of(s);
            if (created != null && created.panelId().equals(Openable.SHULKER.slotPanelId()) && created.getLocalIndex() == i) return s;
        }
        throw new AssertionError("no shulker slot " + i + " on the menu");
    }

    /** How many of {@code item} the open box holds, read from the box itself on the server. */
    private int boxCount(net.minecraft.world.item.Item item) {
        return scene.world().getServer().computeOnServer(server -> {
            Session s = Sessions.get(player(server), Openable.SHULKER);
            if (s == null) return -1;
            int n = 0;
            for (var stack : s.stack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList())
                if (stack.is(item)) n += stack.getCount();
            return n;
        });
    }

    private int carried() {
        return ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().getCount());
    }

    private void hover(Predicate<Slot> p, String what) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = screen(mc);
            Slot found = null;
            for (Slot s : screen.getMenu().slots) if (s.isActive() && p.test(s)) { found = s; break; }
            if (found == null) throw new AssertionError("no " + what + " on " + screen);
            ScreenAccessor edge = (ScreenAccessor) screen;
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(edge.shulkersininventory$leftPos() + found.x + 8) * f, (edge.shulkersininventory$topPos() + found.y + 8) * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
    }

    private void click(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    private void shiftRightClick(Predicate<Slot> p, String what) {
        shiftPress(p, what, InputConstants.MOUSE_BUTTON_RIGHT);
    }

    private void shiftLeftClick(Predicate<Slot> p, String what) {
        shiftPress(p, what, InputConstants.MOUSE_BUTTON_LEFT);
    }

    /**
     * Fabric's TestInput clicks carry no modifiers, and 26.3 reads shift from the click itself,
     * so this presses through the MouseHandler hook with GLFW's shift bit (1) set.
     */
    private void shiftPress(Predicate<Slot> p, String what, int button) {
        hover(p, what);
        for (int action : new int[] {1, 0}) {
            ctx.runOnClient(mc -> ((net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler)
                    .invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(button, 1), action));
            ctx.waitTick();
        }
        ctx.waitTicks(5);
    }
}
