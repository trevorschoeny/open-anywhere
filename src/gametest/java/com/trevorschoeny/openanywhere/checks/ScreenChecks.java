package com.trevorschoeny.openanywhere.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.slotkit.api.menu.MenuSwitch;
import com.trevorschoeny.openanywhere.ContainerHost;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.Settings;
import com.trevorschoeny.openanywhere.Tab;
import com.trevorschoeny.openanywhere.TabId;
import com.trevorschoeny.openanywhere.Tabs;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.openanywhere.api.ShownItem;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import testgallery.SceneWorld;

/**
 * Checks for the screens scene (test-gallery/scenes/open-anywhere/screens.json): every tab
 * is the real screen, the world chest stays open behind the others, the row's order, edits
 * written back to the item, nested items, the cursor across a switch, the lock on the item
 * in front, Escape, and every box getting a tab. Opens are real double-clicks through the
 * mouse; tab clicks go through {@link Tabs#pick}, what a click on a tab calls.
 */
public class ScreenChecks implements FabricClientGameTest {

    private static final String LIME = "minecraft:lime_shulker_box[container=[{slot:0,item:{id:\"minecraft:emerald\",count:3}}]]";
    private static final String BUNDLE = "minecraft:bundle[bundle_contents=[{id:\"minecraft:stone\",count:4},{id:\"minecraft:granite\",count:2}]]";

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private JsonObject station;
    private final List<String> failures = new ArrayList<>();

    // Every frame drawn while recording: the screen, and how many of its container's slots show an item.
    private static volatile boolean recording;
    private static final List<String> FRAMES = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((sc, g, mx, my, delta) -> {
                    if (recording) FRAMES.add(describe(sc));
                })));
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "open-anywhere", "screens")) {
            this.scene = scene;
            run("openanywhere:screens_chest_tabs", this::chestTabs);
            run("openanywhere:screens_inventory_tab", this::inventoryTab);
            run("openanywhere:screens_box_edits", this::boxEdits);
            run("openanywhere:screens_from_inventory", this::fromInventory);
            run("openanywhere:screens_order", this::order);
            run("openanywhere:screens_ender", this::ender);
            run("openanywhere:screens_bundle", this::bundle);
            run("openanywhere:screens_nested", this::nested);
            run("openanywhere:screens_cursor", this::cursor);
            run("openanywhere:screens_lock", this::lock);
            run("openanywhere:screens_escape", this::escape);
            run("openanywhere:screens_every_box", this::everyBox);
            run("openanywhere:screens_no_flash", this::noFlash);
            // With -Popenanywhere.walk: the tabs in use, frame by frame, for a WebP.
            if (Boolean.getBoolean("openanywhere.walk")) run("openanywhere:screens_every_box", this::walkthrough);
        }
        if (!failures.isEmpty()) throw new AssertionError("screen checks failed:\n- " + String.join("\n- ", failures));
    }

    /** One station, from a clean survival player holding a lime box, an ender chest and a bundle. */
    private void run(String id, Runnable check) {
        station = scene.station(id);
        try {
            ctx.runOnClient(mc -> Settings.reset());
            scene.standAt(station, 30);
            for (String c : new String[] {"gamemode survival @p", "clear @p",
                    "item replace entity @p hotbar.0 with " + LIME,
                    "item replace entity @p hotbar.1 with minecraft:ender_chest",
                    "item replace entity @p hotbar.2 with " + BUNDLE,
                    "item replace entity @p inventory.0 with minecraft:dirt 64",
                    "item replace entity @p enderchest.4 with minecraft:green_shulker_box[container=[{slot:0,item:{id:\"minecraft:cactus\",count:8}}]]"})
                command(c);
            ctx.waitTicks(3);
            check.run();
        } catch (AssertionError e) {
            failures.add(id + ": " + e.getMessage());
        } finally {
            ctx.takeScreenshot("screens-" + id.substring(id.indexOf(':') + 1));
            ctx.runOnClient(mc -> { if (mc.player != null) mc.player.closeContainer(); });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> Settings.reset());
        }
    }

    // Stations

    /** A chest opens on its own tab beside the inventory's; a box in it opens on vanilla's shulker box screen as a third. */
    private void chestTabs() {
        openBlock();
        expectRow("INVENTORY, WORLD", "the chest just opened");
        expectSelected(TabId.WORLD, "the chest just opened");
        doubleClick(containerSlot(4), "the purple box in the chest");
        expectScreen(ShulkerBoxScreen.class, "double-click on the box");
        expectRow("INVENTORY, WORLD, purple_shulker_box", "the box opened");
        int diamonds = ctx.computeOnClient(mc -> {
            ItemStack first = screen(mc).getMenu().getSlot(0).getItem();
            return first.is(Items.DIAMOND) ? first.getCount() : 0;
        });
        if (diamonds != 5) throw new AssertionError("the box's screen shows " + diamonds + " diamonds in its first slot, not 5");
        if (!setAside()) throw new AssertionError("the chest isn't waiting open behind the box's screen");
        expectChestOpen(true, "the box's screen in front");
    }

    /** The inventory tab is the real inventory screen; the chest stays open behind it and comes back. */
    private void inventoryTab() {
        openBlock();
        pick(TabId.INVENTORY);
        expectScreen(InventoryScreen.class, "the Inventory tab");
        expectSelected(TabId.INVENTORY, "the Inventory tab");
        ctx.waitTicks(12); // past the chest's opener recheck
        expectChestOpen(true, "the inventory tab in front");
        pick(TabId.WORLD);
        expectScreen(ContainerScreen.class, "the Chest tab");
        boolean cobble = ctx.computeOnClient(mc -> screen(mc).getMenu().getSlot(0).getItem().is(Items.COBBLESTONE));
        if (!cobble) throw new AssertionError("the chest came back without its cobblestone");
    }

    /**
     * Taking from a box opened in a chest takes from the box itself, and what the box holds
     * past vanilla's 27 slots (another mod's extra rows) stays put.
     */
    private void boxEdits() {
        scene.world().getServer().runOnServer(server -> {
            ItemStack box = chest(server).getItem(4);
            List<ItemStack> items = new ArrayList<>(box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList());
            while (items.size() < 41) items.add(ItemStack.EMPTY);
            items.set(40, new ItemStack(Items.CACTUS, 8));
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        });
        openBlock();
        doubleClick(containerSlot(4), "the purple box in the chest");
        expectScreen(ShulkerBoxScreen.class, "double-click on the box");
        shiftClick(s -> !(s.container instanceof Inventory) && s.getItem().is(Items.DIAMOND), "the diamonds in the box");
        int left = scene.world().getServer().computeOnServer(server -> count(chest(server).getItem(4), Items.DIAMOND));
        if (left != 0) throw new AssertionError("the box in the chest still holds " + left + " diamonds after taking them");
        int carried = ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.DIAMOND));
        if (carried != 5) throw new AssertionError("the inventory has " + carried + " diamonds, not 5");
        int kept = scene.world().getServer().computeOnServer(server -> count(chest(server).getItem(4), Items.CACTUS));
        if (kept != 8) throw new AssertionError("the box's slot 41 held 8 cactus; after an edit it holds " + kept);
    }

    /** From the inventory: the box's screen, back to the inventory with the box shown open, then double-clicked shut. */
    private void fromInventory() {
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        expectRow("INVENTORY, lime_shulker_box", "the lime box opened");
        pick(TabId.INVENTORY);
        expectScreen(InventoryScreen.class, "the Inventory tab");
        expectRow("INVENTORY, lime_shulker_box", "back on the inventory");
        doubleClick(hotbar(0), "the open lime box");
        if (!ctx.computeOnClient(mc -> Tabs.row().isEmpty())) throw new AssertionError("the box's tab stayed after double-clicking it shut: " + rowNames());
    }

    /** The row follows where items sit, not the order they opened: the hotbar's first, then the chest's, slot by slot. */
    private void order() {
        openBlock();
        doubleClick(containerSlot(1), "the orange box");
        pick(TabId.WORLD);
        expectScreen(ContainerScreen.class, "the Chest tab");
        doubleClick(containerSlot(0), "the red box");
        pick(TabId.WORLD);
        expectScreen(ContainerScreen.class, "the Chest tab");
        doubleClick(hotbar(0), "the lime box");
        expectRow("INVENTORY, WORLD, lime_shulker_box, red_shulker_box, orange_shulker_box", "three boxes opened out of order");
        // Groups: the lime box is the inventory's, the red and orange the chest's; only the
        // group holding the tab in front is open.
        String groups = ctx.computeOnClient(mc -> names(Tabs.children(Tabs.Group.INVENTORY)) + " | " + names(Tabs.children(Tabs.Group.WORLD))
                + " | open " + Tabs.expanded(Tabs.Group.INVENTORY) + " " + Tabs.expanded(Tabs.Group.WORLD));
        if (!groups.equals("lime_shulker_box | red_shulker_box, orange_shulker_box | open true false"))
            throw new AssertionError("the groups read [" + groups + "]");
        // The wheel: down from the inventory's last sub-tab is the chest's header; up from
        // there is back into the inventory group at its last sub-tab.
        ctx.getInput().setCursorPos(2, 2);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(5);
        expectSelected(TabId.WORLD, "the wheel down from the lime box");
        ctx.getInput().scroll(1);
        ctx.waitTicks(5);
        String back = ctx.computeOnClient(mc -> name(Tabs.tab(Tabs.selected()).item()));
        if (!back.equals("lime_shulker_box")) throw new AssertionError("the wheel up from the chest's header went to " + back + ", not the lime box");
    }

    private static String names(List<TabId> ids) {
        List<String> names = new ArrayList<>();
        for (TabId id : ids) names.add(name(Tabs.tab(id).item()));
        return String.join(", ", names);
    }

    /** The ender chest opens on vanilla's own ender chest screen over the player's own ender chest. */
    private void ender() {
        openInventory();
        doubleClick(hotbar(1), "the ender chest");
        expectScreen(ContainerScreen.class, "double-click on the ender chest");
        boolean green = ctx.computeOnClient(mc -> name(screen(mc).getMenu().getSlot(4).getItem()).equals("green_shulker_box"));
        if (!green) throw new AssertionError("the ender chest's screen doesn't show the green box in its slot 4");
        String title = ctx.computeOnClient(mc -> screen(mc).getTitle().getString());
        if (!title.equals("Ender Chest")) throw new AssertionError("the ender chest's screen is titled " + title);
    }

    /** A bundle opens on the host, one slot per stack plus the add slot. */
    private void bundle() {
        openInventory();
        doubleClick(hotbar(2), "the bundle");
        expectScreen(ContainerScreen.class, "double-click on the bundle");
        String slots = ctx.computeOnClient(mc -> {
            var screen = screen(mc);
            if (!ContainerHost.is(screen.getMenu())) return "not the host: " + screen.getMenu();
            List<String> shown = new ArrayList<>();
            for (Slot s : screen.getMenu().slots) {
                if (Groups.bundleIndex(s) >= 0 && s.isActive()) shown.add(s.getItem().isEmpty() ? "+" : s.getItem().getCount() + " " + name(s.getItem()));
            }
            return String.join(", ", shown);
        });
        if (!slots.equals("4 stone, 2 granite, +")) throw new AssertionError("the bundle's slots show [" + slots + "], not [4 stone, 2 granite, +]");
    }

    /** A bundle inside a box in a chest opens as its own tab, and taking from it takes from the bundle in the box. */
    private void nested() {
        openBlock();
        doubleClick(containerSlot(4), "the cyan box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the cyan box");
        doubleClick(s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0, "the bundle in the box");
        expectScreen(ContainerScreen.class, "double-click on the bundle in the box");
        expectRow("INVENTORY, WORLD, cyan_shulker_box, bundle", "the bundle in the box opened");
        shiftClick(s -> Groups.bundleIndex(s) == 0, "the stone in the bundle");
        int left = scene.world().getServer().computeOnServer(server -> {
            ItemStack box = chest(server).getItem(4);
            ItemStack inner = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
            BundleContents contents = inner.get(DataComponents.BUNDLE_CONTENTS);
            return contents == null ? -1 : contents.size();
        });
        if (left != 0) throw new AssertionError("the bundle in the box holds " + left + " stacks after taking its stone");
    }

    /** An item on the cursor rides along to the next tab's screen. */
    private void cursor() {
        openBlock();
        doubleClick(containerSlot(4), "the purple box");
        pick(TabId.WORLD);
        expectScreen(ContainerScreen.class, "the Chest tab");
        click(containerSlot(0), "the cobblestone");
        TabId box = ctx.computeOnClient(mc -> Tabs.row().get(2).id());
        pick(box);
        expectScreen(ShulkerBoxScreen.class, "the box's tab with cobblestone on the cursor");
        int carried = ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().is(Items.COBBLESTONE) ? screen(mc).getMenu().getCarried().getCount() : 0);
        if (carried != 40) throw new AssertionError("the cursor holds " + carried + " cobblestone on the box's screen, not 40");
        int server = scene.world().getServer().computeOnServer(s -> player(s).containerMenu.getCarried().getCount());
        if (server != 40) throw new AssertionError("the server's cursor holds " + server + " on the box's menu, not 40");
    }

    /** The box in front can't be lifted out from under its own screen. */
    private void lock() {
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        click(hotbar(0), "the open lime box");
        int carried = ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().getCount());
        if (carried != 0) throw new AssertionError("the open box was picked up");
        boolean there = scene.world().getServer().computeOnServer(s -> name(player(s).getInventory().getItem(0)).equals("lime_shulker_box"));
        if (!there) throw new AssertionError("the open box left its slot");
    }

    /** Escape closes the screens and the chest behind them; opening it again starts fresh. */
    private void escape() {
        openBlock();
        doubleClick(containerSlot(4), "the purple box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the box");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(12);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("Escape left a screen open");
        expectChestOpen(false, "Escape");
        openBlock();
        expectRow("INVENTORY, WORLD", "the chest opened again after Escape");
    }

    /** Every box gets a tab: all of them in reach, in order, the bundle inside the cyan box too; the wheel moves between them. */
    private void everyBox() {
        ctx.runOnClient(mc -> Settings.setEveryBox(true));
        ctx.waitTicks(3);
        openBlock();
        expectRow("INVENTORY, WORLD, lime_shulker_box, ender_chest, bundle, red_shulker_box, orange_shulker_box, cyan_shulker_box, bundle, green_shulker_box",
                "the chest opened with every box tabbed");
        TabId red = ctx.computeOnClient(mc -> Tabs.row().get(5).id());
        pick(red);
        expectScreen(ShulkerBoxScreen.class, "the red box's tab");
        // A double-click on a box jumps to its tab.
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        String shown = ctx.computeOnClient(mc -> {
            Tab front = Tabs.shownItem();
            return front == null ? "none" : name(front.item());
        });
        if (!shown.equals("lime_shulker_box")) throw new AssertionError("double-click jumped to " + shown + ", not the lime box");
        // The server sees the box in front: what other mods read.
        String api = scene.world().getServer().computeOnServer(s -> {
            OpenItems.OpenItem item = OpenItems.shown(player(s));
            return item == null ? "none" : name(item.stack());
        });
        if (!api.equals("lime_shulker_box")) throw new AssertionError("OpenItems.shown is " + api + " on the server");
        if (ctx.computeOnClient(mc -> ShownItem.get() == null)) throw new AssertionError("ShownItem.get() is null with the lime box in front");
    }

    /**
     * Not a check: the tabs in use, screenshotted as fast as screenshots come and named by
     * the milliseconds since it started, stitched into a WebP. Opens the chest, two boxes,
     * goes to the inventory and back, then every box gets a tab and the wheel runs along them.
     */
    private void walkthrough() {
        long start = System.currentTimeMillis();
        openBlock();
        film(start, 700);
        doubleClick(containerSlot(0), "the red box");
        film(start, 900);
        pick(TabId.WORLD);
        film(start, 600);
        doubleClick(containerSlot(4), "the cyan box");
        film(start, 900);
        doubleClick(s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0, "the bundle in the cyan box");
        film(start, 900);
        pick(TabId.INVENTORY);
        film(start, 900);
        pick(TabId.WORLD);
        film(start, 900);
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> Settings.setEveryBox(true));
        openBlock();
        film(start, 700);
        List<TabId> ids = ctx.computeOnClient(mc -> {
            List<TabId> all = new ArrayList<>();
            for (Tab tab : Tabs.row()) all.add(tab.id());
            return all;
        });
        for (TabId id : ids.subList(2, ids.size())) {
            pick(id);
            film(start, 500);
        }
    }

    /** Screenshots for {@code millis}, each named by its time since {@code start}. */
    private void film(long start, long millis) {
        // The mouse off every slot, so no tooltip covers the screen.
        ctx.getInput().setCursorPos(2, 2);
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until)
            ctx.takeScreenshot("walk-" + String.format("%06d", System.currentTimeMillis() - start));
    }

    /**
     * No frame shows a switched-to screen empty: each switch is filmed frame by frame (every
     * frame drawn, not screenshots), and a frame of the new screen with nothing in its
     * container's slots is a flash. The box holds diamonds, the bundle stone, the chest cobblestone.
     */
    private void noFlash() {
        openBlock();
        switchAndWatch(() -> doubleClickNow(containerSlot(4), "the purple box"), "ShulkerBoxScreen", "opening the box");
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(TabId.WORLD)), "ContainerScreen", "back to the chest");
        switchAndWatch(() -> doubleClickNow(hotbar(2), "the bundle"), "ContainerScreen", "opening the bundle");
        TabId box = tabOf("purple_shulker_box");
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(box)), "ShulkerBoxScreen", "the box's tab from the bundle");
        TabId bundle = tabOf("bundle");
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(bundle)), "ContainerScreen", "the bundle's tab from the box");
    }

    /** The row's tab for the item named {@code name}. */
    private TabId tabOf(String name) {
        return ctx.computeOnClient(mc -> {
            for (Tab tab : Tabs.row()) if (tab.id().type() == TabId.Type.ITEM && name(tab.item()).equals(name)) return tab.id();
            throw new AssertionError("no " + name + " tab on the row");
        });
    }

    /** Runs {@code action}, records every frame for half a second, and fails on a frame of {@code screen} with an empty container. */
    private void switchAndWatch(Runnable action, String screen, String what) {
        FRAMES.clear();
        recording = true;
        action.run();
        ctx.waitTicks(10);
        recording = false;
        List<String> frames;
        synchronized (FRAMES) { frames = new ArrayList<>(FRAMES); }
        if (frames.stream().noneMatch(f -> f.startsWith(screen + ":") && !f.endsWith(":0")))
            throw new AssertionError(what + ": " + screen + " never showed filled; frames " + frames);
        for (String f : frames) {
            if (f.equals(screen + ":0")) throw new AssertionError(what + ": a frame drew " + screen + " empty (a flash); frames " + frames);
        }
    }

    /** A frame, as {@code ScreenClass:items}: how many of the screen's own container slots (not the inventory's) show an item. */
    private static String describe(net.minecraft.client.gui.screens.Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return screen.getClass().getSimpleName() + ":-";
        int items = 0;
        for (Slot s : container.getMenu().slots) if (!(s.container instanceof Inventory) && s.isActive() && s.hasItem()) items++;
        return screen.getClass().getSimpleName() + ":" + items;
    }

    // Reads

    /** The row as names: the inventory and world tabs by type, items by their item's id. */
    private String rowNames() {
        return ctx.computeOnClient(mc -> {
            List<String> names = new ArrayList<>();
            for (Tab tab : Tabs.row()) names.add(tab.id().type() == TabId.Type.ITEM ? name(tab.item()) : tab.id().type().name());
            return String.join(", ", names);
        });
    }

    private void expectRow(String expected, String after) {
        ctx.waitTicks(3);
        String row = rowNames();
        if (!row.equals(expected)) throw new AssertionError("after " + after + " the row reads [" + row + "], not [" + expected + "]");
    }

    private void expectSelected(TabId id, String after) {
        TabId selected = ctx.computeOnClient(mc -> Tabs.selected());
        if (!selected.equals(id)) throw new AssertionError("after " + after + " the selected tab is " + selected + ", not " + id);
    }

    private void expectScreen(Class<?> type, String after) {
        ctx.waitTicks(5);
        Object screen = ctx.computeOnClient(mc -> mc.gui.screen());
        if (!type.isInstance(screen)) throw new AssertionError(after + " shows " + screen + ", not a " + type.getSimpleName());
    }

    private boolean setAside() {
        return scene.world().getServer().computeOnServer(s -> MenuSwitch.setAside(player(s)).isPresent());
    }

    private void expectChestOpen(boolean open, String after) {
        int openers = scene.world().getServer().computeOnServer(s -> ChestBlockEntity.getOpenCount(player(s).level(), block()));
        if ((openers > 0) != open) throw new AssertionError("after " + after + " the chest has " + openers + " openers");
    }

    private static String name(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static int count(ItemStack box, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList())
            if (s.is(item)) n += s.getCount();
        return n;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private Container chest(MinecraftServer server) {
        return (ChestBlockEntity) server.overworld().getBlockEntity(block());
    }

    private static AbstractContainerScreen<?> screen(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) throw new AssertionError("no container screen open");
        return screen;
    }

    // Doing

    private void pick(TabId id) {
        ctx.runOnClient(mc -> Tabs.pick(id));
        ctx.waitTicks(5);
    }

    private void command(String c) {
        scene.world().getServer().runCommand(c);
    }

    /** The station's chest, at {@code [2, 0, 2]} from its origin. */
    private BlockPos block() {
        JsonArray o = station.getAsJsonArray("origin");
        return new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
    }

    /** Stands two blocks north of the station's chest and opens it. */
    private void openBlock() {
        BlockPos b = block();
        command(String.format("tp @p %.1f %d %.1f 0 30", b.getX() + 0.5, b.getY(), b.getZ() - 1.5));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(b);
        ctx.waitTick();
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen)) throw new AssertionError("the chest didn't open");
    }

    private void openInventory() {
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(3);
    }

    private static Predicate<Slot> hotbar(int i) {
        return s -> s.container instanceof Inventory && s.getContainerSlot() == i && s.isActive();
    }

    /** The open screen's own container's slot {@code i}: the chest's, not the player's. */
    private static Predicate<Slot> containerSlot(int i) {
        return s -> !(s.container instanceof Inventory) && s.getContainerSlot() == i && s.isActive();
    }

    private void hover(Predicate<Slot> p, String what) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = screen(mc);
            Slot found = null;
            for (Slot s : screen.getMenu().slots) if (p.test(s)) { found = s; break; }
            if (found == null) throw new AssertionError("no " + what + " on " + screen);
            ScreenAccessor edge = (ScreenAccessor) screen;
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

    /** A double-click, as the real mouse sends it: two left presses inside one client task. */
    private void doubleClick(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int i = 0; i < 2; i++) for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        });
        ctx.waitTicks(8);
    }

    /** A double-click with no wait after it. */
    private void doubleClickNow(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int i = 0; i < 2; i++) for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
        });
    }

    /** A shift-click, with SDL's Left Shift bit, through the mouse hook (TestInput clicks carry no modifiers). */
    private void shiftClick(Predicate<Slot> p, String what) {
        hover(p, what);
        for (int action : new int[] {1, 0}) {
            ctx.runOnClient(mc -> ((net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler)
                    .invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 1), action));
            ctx.waitTick();
        }
        ctx.waitTicks(5);
    }
}
