package com.trevorschoeny.openanywhere.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.menu.MenuSwitch;
import com.trevlar.slotkit.api.storage.StorageContainerAdapter;
import com.trevorschoeny.openanywhere.ContainerHost;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.Heads;
import com.trevorschoeny.openanywhere.OpenC2S;
import com.trevorschoeny.openanywhere.Openable;
import com.trevorschoeny.openanywhere.OpenAnywhereClient;
import com.trevorschoeny.openanywhere.Settings;
import com.trevorschoeny.openanywhere.Tab;
import com.trevorschoeny.openanywhere.TabId;
import com.trevorschoeny.openanywhere.Tabs;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.openanywhere.api.ShownItem;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
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
    // The tab row's layout on every frame drawn while recording: each tab's x and width.
    private static final List<String> LAYOUTS = java.util.Collections.synchronizedList(new ArrayList<>());

    private static String layout() {
        StringBuilder b = new StringBuilder();
        for (var p : com.trevlar.menukit.inject.ContainerTabRows.placedForChecks(com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP))
            b.append(p.x()).append('/').append(p.width()).append(p.clipLeft() > 0 ? "c" : "").append(' ');
        return b.toString().trim();
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((sc, g, mx, my, delta) -> {
                    if (recording) LAYOUTS.add(sc.getClass().getSimpleName() + " " + layout());
                    if (recording) FRAMES.add(describe(sc) + "|" + Tabs.selected().type() + (Tabs.holds(TabId.WORLD, Tabs.selected()) ? "+" : ""));
                })));
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "open-anywhere", "screens")) {
            this.scene = scene;
            run("openanywhere:screens_chest_tabs", this::chestTabs);
            run("openanywhere:screens_chest_tabs", this::openStill);
            run("openanywhere:screens_inventory_tab", this::inventoryTab);
            run("openanywhere:screens_inventory_tab", this::refusedPick);
            run("openanywhere:screens_box_edits", this::boxEdits);
            run("openanywhere:screens_from_inventory", this::fromInventory);
            run("openanywhere:screens_from_inventory", this::boxSlotPath);
            run("openanywhere:screens_from_inventory", this::recipeBookStays);
            run("openanywhere:screens_order", this::order);
            run("openanywhere:screens_ender", this::ender);
            run("openanywhere:screens_ender", this::carriedEnder);
            run("openanywhere:screens_ender", this::carriedEnderTabs);
            run("openanywhere:screens_ender", this::carriedMoved);
            run("openanywhere:screens_ender", this::carriedBox);
            run("openanywhere:screens_bundle", this::bundle);
            run("openanywhere:screens_bundle", this::bundleHalf);
            run("openanywhere:screens_bundle", this::bundleOverfull);
            run("openanywhere:screens_nested", this::nested);
            run("openanywhere:screens_cursor", this::cursor);
            // Off until MenuKit's on-frame tab panel claims only its tabs: it fails today, the click eaten.
            if (Boolean.getBoolean("openanywhere.dropBesideTabs")) run("openanywhere:screens_cursor", this::dropBesideTabs);
            run("openanywhere:screens_lock", this::handPickup);
            run("openanywhere:screens_lock", this::everyPickup);
            run("openanywhere:screens_lock", this::quickClose);
            run("openanywhere:screens_lock", this::lockedWhileCarried);
            run("openanywhere:screens_lock", this::twins);
            run("openanywhere:screens_lock", this::takenAway);
            run("openanywhere:screens_lock", this::twoEnderChests);
            run("openanywhere:screens_box_edits", this::walkedAway);
            run("openanywhere:screens_box_edits", this::hopperTook);
            run("openanywhere:screens_escape", this::escape);
            run("openanywhere:screens_every_box", this::everyBox);
            run("openanywhere:screens_every_box", this::inventoryAlone);
            run("openanywhere:screens_every_box", this::fastWheel);
            run("openanywhere:screens_every_box", this::wheelEverywhere);
            run("openanywhere:screens_every_box", this::treeEvery);
            run("openanywhere:screens_every_box", this::treeHand);
            run("openanywhere:screens_every_box", this::sizesDeep);
            run("openanywhere:screens_no_flash", this::noFlash);
            run("openanywhere:screens_double_chest", this::doubleChest);
            run("openanywhere:screens_world_shulker", this::worldShulkerIcon);
            run("openanywhere:screens_world_shulker", this::worldShulkerNamed);
            run("openanywhere:screens_world_shulker", this::staleOpener);
            run("openanywhere:screens_world_shulker", this::traderHeads);
            run("openanywhere:screens_world_shulker", this::villagerTab);
            run("openanywhere:screens_world_shulker", this::mountTab);
            run("openanywhere:screens_world_shulker", this::wideTallStations);
            // Last at this station: it builds other blocks over the box.
            run("openanywhere:screens_world_shulker", this::everyContainer);
            run("openanywhere:screens_world_shulker", this::recipeBookAtTable);
            run("openanywhere:screens_world_shulker", this::bookFromWorld);
            run("openanywhere:screens_world_shulker", this::everyScreen);
            // With -Popenanywhere.ipn: Inventory Profiles Next's sort on our screens.
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("inventoryprofilesnext")) {
                run("openanywhere:screens_lock", this::ipnBox);
                run("openanywhere:screens_lock", this::ipnBundle);
                run("openanywhere:screens_lock", this::ipnInventory);
            }
            // With -Popenanywhere.walk: the tabs in use, frame by frame, for a WebP.
            if (Boolean.getBoolean("openanywhere.walk")) run("openanywhere:screens_every_box", this::walkthrough);
        }
        if (!failures.isEmpty()) throw new AssertionError("screen checks failed:\n- " + String.join("\n- ", failures));
    }

    /** One station, from a clean survival player holding a lime box, an ender chest and a bundle. */
    private void run(String id, Runnable check) {
        station = scene.station(id);
        try {
            ctx.runOnClient(mc -> { Settings.reset(); Settings.setTabs(Settings.TabMode.HAND); });
            scene.standAt(station, 30);
            for (String c : new String[] {"gamemode survival @p", "clear @p",
                    "item replace entity @p hotbar.0 with " + LIME,
                    "item replace entity @p hotbar.1 with minecraft:ender_chest",
                    "item replace entity @p hotbar.2 with " + BUNDLE,
                    "item replace entity @p inventory.0 with minecraft:dirt 64",
                    "item replace entity @p enderchest.4 with minecraft:green_shulker_box[container=[{slot:0,item:{id:\"minecraft:cactus\",count:8}}]]"})
                command(c);
            pocket(ItemStack.EMPTY);
            ctx.waitTicks(3);
            check.run();
        } catch (AssertionError e) {
            failures.add(id + ": " + e.getMessage());
        } finally {
            ctx.takeScreenshot("screens-" + id.substring(id.indexOf(':') + 1));
            ctx.runOnClient(mc -> { if (mc.player != null) mc.player.closeContainer(); });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> { Settings.reset(); Settings.setTabs(Settings.TabMode.HAND); });
        }
    }

    // Stations

    /**
     * Every box tabbed, a chest opened with the inventory's group full of boxes: the row stands
     * still from the chest's first frame, the chest's group open, nothing sliding in.
     */
    private void openStill() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        for (int i = 0; i < 6; i++) command("item replace entity @p inventory." + (i + 1) + " with minecraft:red_shulker_box");
        ctx.waitTicks(5);
        LAYOUTS.clear();
        recording = true;
        openBlock();
        ctx.waitTicks(10);
        recording = false;
        List<String> frames;
        synchronized (LAYOUTS) { frames = new ArrayList<>(LAYOUTS); }
        List<String> chest = frames.stream().filter(l -> l.startsWith("ContainerScreen")).toList();
        if (chest.isEmpty()) throw new AssertionError("no frame of the chest was drawn");
        if (chest.stream().distinct().count() > 1) throw new AssertionError("the row moved as the chest opened: " + chest.stream().distinct().toList());
    }

    /**
     * A chest opens on its own tab beside the inventory's, in front from its first frame, its
     * group open; a box in it opens on vanilla's shulker box screen as a third.
     */
    private void chestTabs() {
        FRAMES.clear();
        recording = true;
        openBlock();
        recording = false;
        List<String> opening;
        synchronized (FRAMES) { opening = new ArrayList<>(FRAMES); }
        for (String f : opening) {
            if (f.startsWith("ContainerScreen") && !f.endsWith("|WORLD+"))
                throw new AssertionError("a frame of the chest just opened had another tab in front: " + opening);
        }
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
     * A tab click the server refuses (the tab is no longer on its row: an item just moved,
     * another player has it open): the tab drawn selected goes back to the one in front.
     */
    private void refusedPick() {
        openInventory();
        pick(TabId.item(new ItemPath(ItemRef.Source.INVENTORY, 30, List.of())));
        expectSelected(TabId.INVENTORY, "a click on a tab the server doesn't have");
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

    /** Opened by hand from the inventory: the box's screen, then back to the inventory, which closes the box. */
    private void fromInventory() {
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        expectRow("INVENTORY, lime_shulker_box", "the lime box opened");
        pick(TabId.INVENTORY);
        expectScreen(InventoryScreen.class, "the Inventory tab");
        expectRow("", "back on the inventory, the box no longer selected");
    }

    /**
     * A box opened from the inventory names its own slots via {@code ItemSources.pathOf}
     * (SlotKit's {@code ItemContents}), the same path a shift-click out of it resolves
     * through: {@code box.child(i)}, not the box's own slot again.
     */
    private void boxSlotPath() {
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        String got = scene.world().getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ItemPath box = new ItemPath(ItemRef.Source.INVENTORY, 0, List.of());
            for (Slot slot : p.containerMenu.slots) {
                if (slot.container instanceof StorageContainerAdapter) {
                    ItemPath path = ItemSources.pathOf(p, slot);
                    return String.valueOf(box.child(slot.getContainerSlot()).equals(path));
                }
            }
            return "no box slot found";
        });
        if (!got.equals("true")) throw new AssertionError("a box slot's path from ItemSources.pathOf was " + got + ", not box.child(i)");
    }

    /**
     * The recipe book open on the inventory: a box opened stands beside it, the frame's left
     * edge where it was; a click on the book with an item on the cursor isn't a throw; a recipe
     * picked on it brings the Inventory tab back with the grid filled, the book still open.
     */
    private void recipeBookStays() {
        command("recipe give @p minecraft:crafting_table");
        command("item replace entity @p inventory.1 with minecraft:oak_planks 4");
        bookOpen(true);
        try {
            openInventory();
            int left = leftPos();
            doubleClick(hotbar(0), "the lime box");
            expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
            expectBook("the box opened over the open book", left);
            click(slot(9), "the dirt");
            clickBookEdge();
            String dirt = ctx.computeOnClient(mc -> name(mc.player.containerMenu.getCarried()));
            if (!dirt.equals("dirt")) throw new AssertionError("a click on the recipe book with dirt on the cursor left " + dirt + " there");
            click(slot(9), "the dirt's slot");
            clickRecipe(Items.CRAFTING_TABLE, "the crafting table's recipe");
            expectScreen(InventoryScreen.class, "a recipe picked on the box's screen");
            ctx.waitTicks(5);
            int planks = scene.world().getServer().computeOnServer(sv -> {
                int n = 0;
                for (Slot g : player(sv).inventoryMenu.getInputGridSlots()) if (g.getItem().is(Items.OAK_PLANKS)) n += g.getItem().getCount();
                return n;
            });
            if (planks != 4) throw new AssertionError("the inventory's grid holds " + planks + " planks after the recipe was picked, not 4");
            if (ctx.computeOnClient(mc -> !net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen.class.isInstance(mc.gui.screen())
                    || !((com.trevorschoeny.openanywhere.mixin.RecipeBookScreenAccessor) mc.gui.screen()).openanywhere$book().isVisible()))
                throw new AssertionError("the recipe book closed on the way back to the inventory");
            if (leftPos() != left) throw new AssertionError("back on the inventory the frame is at " + leftPos() + ", not " + left);
        } finally {
            bookOpen(false);
        }
    }

    /**
     * The recipe book open on a crafting table: a box from the inventory keeps it, and a recipe
     * picked there brings the crafting table's tab back with its grid filled.
     */
    private void recipeBookAtTable() {
        command("recipe give @p minecraft:crafting_table");
        command("item replace entity @p inventory.1 with minecraft:oak_planks 4");
        BlockPos b = block();
        command(String.format("setblock %d %d %d minecraft:crafting_table", b.getX(), b.getY(), b.getZ()));
        ctx.waitTicks(3);
        bookOpen(true);
        try {
            openAt(b, net.minecraft.client.gui.screens.inventory.CraftingScreen.class, "the crafting table");
            int left = leftPos();
            doubleClick(hotbar(0), "the lime box");
            expectScreen(ShulkerBoxScreen.class, "double-click on the lime box at the crafting table");
            expectBook("the box opened over the crafting table's open book", left);
            clickRecipe(Items.CRAFTING_TABLE, "the crafting table's recipe");
            expectScreen(net.minecraft.client.gui.screens.inventory.CraftingScreen.class, "a recipe picked on the box's screen");
            ctx.waitTicks(5);
            int planks = scene.world().getServer().computeOnServer(sv -> {
                if (!(player(sv).containerMenu instanceof net.minecraft.world.inventory.CraftingMenu table)) return -1;
                int n = 0;
                for (Slot g : table.getInputGridSlots()) if (g.getItem().is(Items.OAK_PLANKS)) n += g.getItem().getCount();
                return n;
            });
            if (planks != 4) throw new AssertionError("the crafting table's grid holds " + planks + " planks after the recipe was picked, not 4");
        } finally {
            bookOpen(false);
        }
    }

    /**
     * The inventory's recipe book open, then a chest, a box, a crafting table and a furnace
     * (its own book closed) opened from the world: each starts with the book open, and the
     * Inventory tab has the frame where it was. With the inventory's book closed, a chest opens
     * without one.
     */
    private void bookFromWorld() {
        BlockPos b = block();
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        List<String> broken = new ArrayList<>();
        try {
            for (Object[] s : new Object[][] {
                    {"chest", ContainerScreen.class}, {"green_shulker_box", ShulkerBoxScreen.class},
                    {"crafting_table", net.minecraft.client.gui.screens.inventory.CraftingScreen.class},
                    {"furnace", net.minecraft.client.gui.screens.inventory.FurnaceScreen.class}}) {
                String id = (String) s[0];
                command(String.format("setblock %d %d %d minecraft:%s", b.getX(), b.getY(), b.getZ(), id));
                ctx.waitTicks(3);
                bookOpen(true);
                ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.FURNACE, false));
                try {
                    openAt(b, (Class<?>) s[1], "the " + id);
                    if (!ctx.computeOnClient(mc -> bookShown(mc.gui.screen()))) {
                        broken.add("the " + id + " opened from the world without the inventory's open book");
                        continue;
                    }
                    int left = leftPos();
                    pick(TabId.INVENTORY);
                    expectScreen(InventoryScreen.class, "the Inventory tab at the " + id);
                    if (!ctx.computeOnClient(mc -> bookShown(mc.gui.screen()))) broken.add("the Inventory tab at the " + id + " has its book closed");
                    if (leftPos() != left) broken.add("the Inventory tab at the " + id + " has the frame at " + leftPos() + ", not " + left);
                } finally {
                    ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                    ctx.waitTicks(5);
                }
            }
            command(String.format("setblock %d %d %d minecraft:chest", b.getX(), b.getY(), b.getZ()));
            ctx.waitTicks(3);
            bookOpen(false);
            openAt(b, ContainerScreen.class, "the chest");
            if (ctx.computeOnClient(mc -> bookShown(mc.gui.screen()))) broken.add("the chest opened with a book though the inventory's was closed");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
        } finally {
            bookOpen(false);
            ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
        }
        if (!broken.isEmpty()) throw new AssertionError(String.join("\n    ", broken));
    }

    /** Whether {@code screen} shows a recipe book: its own open, or a borrowed one. */
    private static boolean bookShown(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen<?> rb)
            return ((com.trevorschoeny.openanywhere.mixin.RecipeBookScreenAccessor) rb).openanywhere$book().isVisible();
        return com.trevorschoeny.openanywhere.RecipeBookCarry.shownOn(screen) != null;
    }

    /** The crafting book open or closed, showing only what can be crafted. */
    private void bookOpen(boolean open) {
        ctx.runOnClient(mc -> {
            mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, open);
            mc.player.getRecipeBook().setFiltering(net.minecraft.world.inventory.RecipeBookType.CRAFTING, open);
        });
        ctx.waitTick();
    }

    private int leftPos() {
        return ctx.computeOnClient(mc -> ((ScreenAccessor) screen(mc)).openanywhere$leftPos());
    }

    private void expectBook(String after, int left) {
        boolean shown = ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.RecipeBookCarry.shownOn(mc.gui.screen()) != null);
        if (!shown) throw new AssertionError("after " + after + " the recipe book is gone");
        if (leftPos() != left) throw new AssertionError("after " + after + " the frame is at " + leftPos() + ", not " + left + " beside the book");
    }

    /** A click on the recipe book's bottom left corner, clear of its buttons. */
    private void clickBookEdge() {
        double[] at = ctx.computeOnClient(mc -> {
            ScreenAccessor edge = (ScreenAccessor) screen(mc);
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(edge.openanywhere$leftPos() - 140) * f, (edge.openanywhere$topPos() + 160) * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    /** A click on the borrowed recipe book's button for {@code result}, where the page draws it. */
    @SuppressWarnings("unchecked")
    private void clickRecipe(net.minecraft.world.item.Item result, String what) {
        double[] at = ctx.computeOnClient(mc -> {
            var book = com.trevorschoeny.openanywhere.RecipeBookCarry.shownOn(mc.gui.screen());
            if (book == null) throw new AssertionError("no recipe book on " + mc.gui.screen() + " for " + what);
            try {
                var pageField = net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class.getDeclaredField("recipeBookPage");
                pageField.setAccessible(true);
                Object page = pageField.get(book);
                var buttonsField = page.getClass().getDeclaredField("buttons");
                buttonsField.setAccessible(true);
                var w = mc.getWindow();
                double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
                for (var b : (List<net.minecraft.client.gui.screens.recipebook.RecipeButton>) buttonsField.get(page))
                    if (b.visible && b.getDisplayStack().is(result))
                        return new double[] {(b.getX() + b.getWidth() / 2.0) * f, (b.getY() + b.getHeight() / 2.0) * f};
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("reading the recipe book's page: " + e);
            }
            throw new AssertionError("no button for " + what + " on the recipe book's page");
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    /**
     * Every box tabbed: each container's boxes are its sub-tabs, by where they sit, not the
     * order they opened. The wheel walks the same line: up from the chest is the inventory's
     * last box, down from the chest is into its first, selected and small: it holds no boxes.
     */
    private void order() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        expectTree("INVENTORY WORLD*[red_shulker_box, orange_shulker_box]", "the chest opened");
        ctx.getInput().setCursorPos(2, 2);
        ctx.getInput().scroll(1);
        ctx.waitTicks(5);
        String up = ctx.computeOnClient(mc -> name(Tabs.tab(Tabs.selected()).item()));
        if (!up.equals("bundle")) throw new AssertionError("the wheel up from the chest went to " + up + ", not the bundle");
        ctx.getInput().scroll(-1);
        ctx.waitTicks(5);
        expectSelected(TabId.WORLD, "the wheel down from the bundle");
        ctx.getInput().scroll(-1);
        ctx.waitTicks(5);
        expectTree("INVENTORY WORLD[red_shulker_box*, orange_shulker_box]", "the wheel down from the chest");
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

    // A carried attachment: another mod's slot for things the player carries ({@link CarriedSlot})

    /** An ender chest in a carried slot opens the player's ender chest, and stays in front. */
    private void carriedEnder() {
        command("item replace entity @p hotbar.1 with minecraft:air");
        pocket(new ItemStack(Items.ENDER_CHEST));
        openInventory();
        openPocket();
        expectScreen(ContainerScreen.class, "a double-click on the ender chest in the carried slot");
        String title = ctx.computeOnClient(mc -> screen(mc).getTitle().getString());
        if (!title.equals("Ender Chest")) throw new AssertionError("the carried ender chest's screen is titled " + title);
        expectShownFrom(ItemRef.Source.CARRIED, "the carried ender chest opened");
        // Its slot's stack is the same object tick after tick, so it isn't taken for moved.
        ctx.waitTicks(20);
        expectScreen(ContainerScreen.class, "20 ticks with the carried ender chest in front");
        expectShownFrom(ItemRef.Source.CARRIED, "20 ticks with the carried ender chest in front");
        // The client knows its slot as the open one: what draws its lid and lets a double-click close it.
        String open = ctx.computeOnClient(mc -> {
            for (Slot slot : mc.player.containerMenu.slots) {
                ItemPath path = ItemSources.pathOf(mc.player, slot);
                if (path != null && path.source() == ItemRef.Source.CARRIED) return String.valueOf(Tabs.looksOpen(slot));
            }
            return "no carried slot";
        });
        if (!open.equals("true")) throw new AssertionError("with the carried ender chest in front, its slot looks open: " + open);
        openPocket();
        expectScreen(InventoryScreen.class, "a second double-click on the carried ender chest");
    }

    /**
     * Every box tabbed, Pocket Ender Chest's ender chest: a sub-tab of the inventory until it's
     * opened, then a group tab after the inventory's that stays on the row when something else
     * is selected, its boxes in under it then; its lid, on the tab and in its slot alike, up only
     * while it holds the tab in front.
     */
    private void carriedEnderTabs() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        command("item replace entity @p hotbar.1 with minecraft:air");
        pocket(new ItemStack(Items.ENDER_CHEST));
        openInventory();
        expectTree("INVENTORY*[lime_shulker_box, bundle, ender_chest]", "an ender chest in the carried slot, not yet opened");
        pick(tabOf("ender_chest"));
        expectScreen(ContainerScreen.class, "the carried ender chest's tab");
        expectTree("INVENTORY ender_chest*[green_shulker_box]", "the carried ender chest opened");
        pick(TabId.INVENTORY);
        expectTree("INVENTORY*[lime_shulker_box, bundle] ender_chest", "the inventory selected after the carried ender chest");
        String look = ctx.computeOnClient(mc -> {
            for (Slot slot : mc.player.containerMenu.slots) {
                ItemPath path = ItemSources.pathOf(mc.player, slot);
                if (path != null && path.source() == ItemRef.Source.CARRIED)
                    return Tabs.looksOpen(slot) + " " + Tabs.open(TabId.item(path));
            }
            return "no carried slot";
        });
        // It keeps its tab, but its lid shuts as a left box's does.
        if (!look.equals("false false")) throw new AssertionError("the carried ender chest, kept behind the inventory: slot open, tab open = " + look);
        pick(tabOf("green_shulker_box"));
        expectTree("INVENTORY ender_chest[green_shulker_box*]", "the green box in the ender chest selected");
        // Holding the box in front, it's open again.
        String inside = ctx.computeOnClient(mc -> {
            for (Slot slot : mc.player.containerMenu.slots) {
                ItemPath path = ItemSources.pathOf(mc.player, slot);
                if (path != null && path.source() == ItemRef.Source.CARRIED)
                    return Tabs.looksOpen(slot) + " " + Tabs.open(TabId.item(path));
            }
            return "no carried slot";
        });
        if (!inside.equals("true true")) throw new AssertionError("the carried ender chest, holding the box in front: slot open, tab open = " + inside);
        pick(TabId.INVENTORY);
        expectTree("INVENTORY*[lime_shulker_box, bundle] ender_chest", "back on the inventory from the green box");
    }

    /** Every box tabbed, the ender chest in front moved into the carried slot and back: its screen stays, following it. */
    private void carriedMoved() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(1), "the ender chest");
        expectScreen(ContainerScreen.class, "double-click on the ender chest");
        scene.world().getServer().runOnServer(s -> {
            Inventory inv = player(s).getInventory();
            CarriedSlot.POCKET.bind(player(s)).setStack(0, inv.getItem(1));
            inv.setItem(1, ItemStack.EMPTY);
        });
        ctx.waitTicks(5);
        expectScreen(ContainerScreen.class, "the ender chest in front moved into the carried slot");
        expectShownFrom(ItemRef.Source.CARRIED, "the ender chest in front moved into the carried slot");
        scene.world().getServer().runOnServer(s -> {
            var pocket = CarriedSlot.POCKET.bind(player(s));
            player(s).getInventory().setItem(5, pocket.getStack(0));
            pocket.setStack(0, ItemStack.EMPTY);
        });
        ctx.waitTicks(5);
        expectScreen(ContainerScreen.class, "the ender chest in front moved back from the carried slot");
        expectShownFrom(ItemRef.Source.INVENTORY, "the ender chest in front moved back from the carried slot");
    }

    /** A box in a carried slot opens, stays in front, and what's put in it is saved in the slot. */
    private void carriedBox() {
        scene.world().getServer().runOnServer(s -> {
            Inventory inv = player(s).getInventory();
            CarriedSlot.POCKET.bind(player(s)).setStack(0, inv.getItem(0));
            inv.setItem(0, ItemStack.EMPTY);
        });
        ctx.waitTicks(3);
        openInventory();
        openPocket();
        expectScreen(ShulkerBoxScreen.class, "a double-click on the box in the carried slot");
        ctx.waitTicks(20);
        expectScreen(ShulkerBoxScreen.class, "20 ticks with the carried box in front");
        shiftClick(slot(9), "the dirt");
        String got = scene.world().getServer().computeOnServer(s -> {
            ItemStack box = CarriedSlot.POCKET.bind(player(s)).getStack(0);
            return name(box) + " " + count(box, Items.EMERALD) + " emeralds " + count(box, Items.DIRT) + " dirt";
        });
        if (!got.equals("lime_shulker_box 3 emeralds 64 dirt")) throw new AssertionError("after shift-clicking dirt into the carried box, the slot holds " + got);
    }

    private void pocket(ItemStack stack) {
        scene.world().getServer().runOnServer(s -> CarriedSlot.POCKET.bind(player(s)).setStack(0, stack));
    }

    /** The double-click's request on the carried slot, which has no place on screen in the checks. */
    private void openPocket() {
        int index = scene.world().getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            for (Slot slot : p.containerMenu.slots) {
                ItemPath path = ItemSources.pathOf(p, slot);
                if (path != null && path.source() == ItemRef.Source.CARRIED) return slot.index;
            }
            return -1;
        });
        if (index < 0) throw new AssertionError("no carried slot on " + ctx.computeOnClient(mc -> mc.gui.screen()));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new OpenC2S(index, false)));
        ctx.waitTicks(8);
    }

    private void expectShownFrom(ItemRef.Source source, String after) {
        String shown = ctx.computeOnClient(mc -> String.valueOf(Tabs.shown().path()));
        if (!shown.startsWith("ItemPath[source=" + source + ",")) throw new AssertionError("after " + after + " the tab in front is " + shown);
    }

    /** A bundle opens on the host, one slot per stack plus the add slot. */
    private void bundle() {
        openInventory();
        int before = hotbarY();
        doubleClick(hotbar(2), "the bundle");
        expectScreen(ContainerScreen.class, "double-click on the bundle");
        // Opened by hand, the inventory stays where it was: the bundle's slots and the row go above it.
        if (hotbarY() != before) throw new AssertionError("the hotbar moved from y " + before + " to " + hotbarY() + " as the bundle opened");
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

    /**
     * A bundle holding more stacks than it has slots (70 items that stack to 99, one each,
     * which vanilla's weight rule lets in): it opens on its 65 slots, and taking one out
     * keeps the 5 it doesn't show.
     */
    private void bundleOverfull() {
        scene.world().getServer().runOnServer(server -> {
            List<net.minecraft.world.item.ItemStackTemplate> items = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                if (items.size() == 70) break;
                ItemStack one = new ItemStack(item);
                if (one.isEmpty() || one.getMaxStackSize() != 64 || Openable.of(one) != null) continue;
                one.set(DataComponents.MAX_STACK_SIZE, 99);
                items.add(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(one));
            }
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
            player(server).getInventory().setItem(2, bundle);
        });
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(2), "the bundle of 70 stacks");
        expectScreen(ContainerScreen.class, "double-click on the bundle of 70 stacks");
        int shown = ctx.computeOnClient(mc -> {
            int n = 0;
            for (Slot s : screen(mc).getMenu().slots) if (Groups.bundleIndex(s) >= 0 && s.isActive() && s.hasItem()) n++;
            return n;
        });
        if (shown != Openable.BUNDLE.slots) throw new AssertionError("the bundle of 70 stacks shows " + shown + " of them, not " + Openable.BUNDLE.slots);
        shiftClick(s -> Groups.bundleIndex(s) == 0, "the bundle's first stack");
        int left = scene.world().getServer().computeOnServer(server ->
                player(server).getInventory().getItem(2).getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).size());
        if (left != 69) throw new AssertionError("after taking one of its 70 stacks the bundle holds " + left);
    }

    /**
     * Half a stack out of a bundle and back, at a hand's pace and faster: right-click takes
     * half, a click on its slot puts it back, the add slot adds it, a right-click puts one.
     * After each, the client shows what the server has and no item has doubled or gone.
     */
    private void bundleHalf() {
        openInventory();
        doubleClick(hotbar(2), "the bundle");
        expectScreen(ContainerScreen.class, "double-click on the bundle");
        Predicate<Slot> stone = s -> Groups.bundleIndex(s) == 0, granite = s -> Groups.bundleIndex(s) == 1, add = s -> Groups.bundleIndex(s) == 2;
        rightClick(stone, "the stone");
        expectBundle("[2 stone, 2 granite, +] cursor 2 stone", "taking half the stone");
        click(stone, "the stone");
        expectBundle("[4 stone, 2 granite, +] cursor -", "putting the half back on its slot");
        rightClick(stone, "the stone");
        click(add, "the add slot");
        expectBundle("[4 stone, 2 granite, +] cursor -", "putting the half in the add slot");
        rightClick(granite, "the granite");
        rightClick(granite, "the granite");
        expectBundle("[4 stone, 2 granite, +] cursor -", "taking one granite and putting one back");
        // A hand's pace: the next click before the server's answer.
        for (int i = 0; i < 3; i++) {
            pressNow(stone, InputConstants.MOUSE_BUTTON_RIGHT, "the stone");
            pressNow(stone, InputConstants.MOUSE_BUTTON_LEFT, "the stone");
            pressNow(granite, InputConstants.MOUSE_BUTTON_RIGHT, "the granite");
            pressNow(add, InputConstants.MOUSE_BUTTON_LEFT, "the add slot");
        }
        ctx.waitTicks(10);
        expectBundle("[4 stone, 2 granite, +] cursor -", "fast clicks");
    }

    /** The open bundle shows {@code expected}, on the client and the server alike, with 4 stone and 2 granite in all. */
    private void expectBundle(String expected, String after) {
        ctx.waitTicks(5);
        String client = ctx.computeOnClient(mc -> slotsOf(mc.player.containerMenu));
        String server = scene.world().getServer().computeOnServer(sv -> {
            ServerPlayer p = player(sv);
            int stone = 0, granite = 0;
            List<ItemStack> all = new ArrayList<>(p.getInventory().getNonEquipmentItems());
            all.add(p.containerMenu.getCarried());
            BundleContents in = p.getInventory().getItem(2).get(DataComponents.BUNDLE_CONTENTS);
            if (in != null) for (ItemStack i : (Iterable<ItemStack>) in.itemCopies()::iterator) all.add(i);
            for (ItemStack i : all) {
                if (i.is(net.minecraft.world.item.Items.STONE)) stone += i.getCount();
                if (i.is(net.minecraft.world.item.Items.GRANITE)) granite += i.getCount();
            }
            return slotsOf(p.containerMenu) + (stone == 4 && granite == 2 ? "" : " with " + stone + " stone, " + granite + " granite in all");
        });
        if (!client.equals(server)) throw new AssertionError("after " + after + " the client shows " + client + ", the server has " + server);
        if (!server.equals(expected)) throw new AssertionError("after " + after + " the bundle is " + server + ", not " + expected);
    }

    // Inventory Profiles Next

    private static final String MESSY = "minecraft:lime_shulker_box[container=[{slot:5,item:{id:\"minecraft:cobblestone\",count:10}},"
            + "{slot:2,item:{id:\"minecraft:dirt\",count:3}},{slot:9,item:{id:\"minecraft:cobblestone\",count:20}},{slot:20,item:{id:\"minecraft:emerald\",count:1}}]]";

    /** IPN's sort key over {@code p}'s slot. */
    private void ipnSort(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.getInput().pressKey(InputConstants.KEY_R);
        ctx.waitTicks(20);
    }

    /** IPN's sort on an open box: the box's contents sorted in the box, nothing lost or doubled. */
    private void ipnBox() {
        command("item replace entity @p hotbar.0 with " + MESSY);
        openInventory();
        doubleClick(hotbar(0), "the messy box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the messy box");
        ipnSort(containerSlot(0), "the box's first slot");
        ctx.takeScreenshot("screens-ipn-box");
        String got = scene.world().getServer().computeOnServer(s -> {
            ItemStack box = player(s).getInventory().getItem(0);
            return count(box, Items.COBBLESTONE) + " " + count(box, Items.DIRT) + " " + count(box, Items.EMERALD)
                    + " first=" + name(box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne());
        });
        if (!got.startsWith("30 3 1") || got.endsWith("first=air")) throw new AssertionError("after IPN's sort the box holds [" + got + "], not 30 cobblestone, 3 dirt, 1 emerald sorted to the front");
        expectScreen(ShulkerBoxScreen.class, "IPN's sort");
    }

    /** IPN's sort on an open bundle, and on the inventory under it: nothing lost or doubled. */
    private void ipnBundle() {
        openInventory();
        doubleClick(hotbar(2), "the bundle");
        expectScreen(ContainerScreen.class, "double-click on the bundle");
        ipnSort(s -> Groups.bundleIndex(s) == 0, "the bundle's first slot");
        ctx.takeScreenshot("screens-ipn-bundle");
        expectBundleTotals("IPN's sort on the bundle");
        ipnSort(slot(9), "the inventory");
        expectBundleTotals("IPN's sort on the inventory under the bundle");
    }

    /** 4 stone and 2 granite in all, in the bundle, the inventory and the cursor. */
    private void expectBundleTotals(String after) {
        ctx.waitTicks(5);
        String server = scene.world().getServer().computeOnServer(sv -> {
            ServerPlayer p = player(sv);
            int stone = 0, granite = 0;
            List<ItemStack> all = new ArrayList<>(p.getInventory().getNonEquipmentItems());
            all.add(p.containerMenu.getCarried());
            for (ItemStack held : List.copyOf(all)) {
                BundleContents in = held.get(DataComponents.BUNDLE_CONTENTS);
                if (in != null) for (ItemStack i : (Iterable<ItemStack>) in.itemCopies()::iterator) all.add(i);
            }
            for (ItemStack i : all) {
                if (i.is(Items.STONE)) stone += i.getCount();
                if (i.is(Items.GRANITE)) granite += i.getCount();
            }
            return stone + " stone, " + granite + " granite";
        });
        if (!server.equals("4 stone, 2 granite")) throw new AssertionError("after " + after + " there are " + server + " in all, not 4 stone, 2 granite");
    }

    /**
     * IPN's sort on the inventory under an open box, every box tabbed: the box moves with the
     * sort, and its screen follows it, its contents intact.
     */
    private void ipnInventory() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        command("item replace entity @p inventory.20 with " + MESSY);
        command("item replace entity @p inventory.3 with minecraft:stone 5");
        ctx.waitTicks(3);
        openInventory();
        doubleClick(slot(29), "the messy box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the messy box");
        ipnSort(slot(9), "the inventory");
        ctx.takeScreenshot("screens-ipn-inventory");
        expectScreen(ShulkerBoxScreen.class, "IPN's sort on the inventory under the box");
        String at = scene.world().getServer().computeOnServer(s -> {
            var inv = player(s).getInventory();
            for (int i = 0; i < 36; i++) if (count(inv.getItem(i), Items.COBBLESTONE) == 30) return i + " 30";
            return "none";
        });
        String front = ctx.computeOnClient(mc -> String.valueOf(Tabs.shown().path()));
        if (!at.endsWith(" 30") || !front.contains("slot=" + at.split(" ")[0] + ",")) throw new AssertionError("after IPN's sort the box is at [" + at + "] and the screen shows " + front);
    }

    private static String slotsOf(AbstractContainerMenu menu) {
        List<String> shown = new ArrayList<>();
        for (Slot s : menu.slots) {
            if (Groups.bundleIndex(s) >= 0 && (s.isActive() || !s.getItem().isEmpty()))
                shown.add((s.isActive() ? "" : "(hidden)") + (s.getItem().isEmpty() ? "+" : s.getItem().getCount() + " " + name(s.getItem())));
        }
        ItemStack c = menu.getCarried();
        return "[" + String.join(", ", shown) + "] cursor " + (c.isEmpty() ? "-" : c.getCount() + " " + name(c));
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
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        click(containerSlot(0), "the cobblestone");
        pick(tabOf("purple_shulker_box"));
        expectScreen(ShulkerBoxScreen.class, "the box's tab with cobblestone on the cursor");
        int carried = ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().is(Items.COBBLESTONE) ? screen(mc).getMenu().getCarried().getCount() : 0);
        if (carried != 40) throw new AssertionError("the cursor holds " + carried + " cobblestone on the box's screen, not 40");
        int server = scene.world().getServer().computeOnServer(s -> player(s).containerMenu.getCarried().getCount());
        if (server != 40) throw new AssertionError("the server's cursor holds " + server + " on the box's menu, not 40");
    }

    /**
     * A click with an item held in the tab row's strip past its last tab, outside the frame,
     * drops the item into the world as it would without the row: only the tabs take a click.
     * On a chest and on the inventory, whose recipe book decides what's outside its own way.
     */
    private void dropBesideTabs() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        click(slot(9), "the dirt");
        dropPastTabs("the chest");
        command("item replace entity @p inventory.0 with minecraft:dirt 64");
        openInventory();
        click(slot(9), "the dirt");
        dropPastTabs("the inventory");
    }

    /** A left click on the strip past the row's last tab, the item on the cursor dropped by it. */
    private void dropPastTabs(String what) {
        var edge = com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP;
        double[] at = ctx.computeOnClient(mc -> {
            var l = com.trevlar.menukit.inject.ContainerTabRows.lineForChecks(edge);
            if (l == null) throw new AssertionError("no tab row on " + what);
            int end = 0;
            for (var p : com.trevlar.menukit.inject.ContainerTabRows.placedForChecks(edge)) end = Math.max(end, p.x() + p.width());
            double x = end + 12, y = l.rowTop() + l.height() / 2.0;
            int top = ((ScreenAccessor) screen(mc)).openanywhere$topPos();
            if (y >= top) throw new AssertionError("the strip at y " + y + " isn't above " + what + "'s frame top " + top);
            int hit = com.trevlar.menukit.inject.ContainerTabRows.hitForChecks(edge, x, y);
            if (hit != com.trevlar.menukit.inject.TabRowLayout.NONE) throw new AssertionError("past the last tab on " + what + " at " + x + "," + y + " is " + hit + ", not empty");
            return new double[] {x * scale(mc), y * scale(mc)};
        });
        int held = ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().getCount());
        if (held == 0) throw new AssertionError("nothing on the cursor on " + what);
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        int carried = ctx.computeOnClient(mc -> screen(mc).getMenu().getCarried().getCount());
        int server = scene.world().getServer().computeOnServer(s -> player(s).containerMenu.getCarried().getCount());
        if (carried != 0 || server != 0)
            throw new AssertionError("a click beside the tabs on " + what + " kept " + carried + " on the cursor (server " + server + "), not dropped");
    }

    /**
     * Opened by hand, the box in front picked up from its own screen: once the double-click
     * window ends, its tab closes and the screen goes back to the inventory. Put down in
     * another slot, it opens again there, in front.
     */
    private void handPickup() {
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        click(hotbar(0), "the open lime box");
        // Till the double-click window ends (a second, the cursor still), the box keeps its tab.
        expectScreen(ShulkerBoxScreen.class, "lifting the open box, the double-click window open");
        expectRow("INVENTORY, lime_shulker_box", "lifting the open box, the double-click window open");
        ctx.waitTicks(25);
        expectScreen(InventoryScreen.class, "picking up the open box");
        expectRow("", "picking up the open box");
        click(slot(14), "an empty inventory slot");
        expectScreen(ShulkerBoxScreen.class, "putting the box down");
        expectRow("INVENTORY, lime_shulker_box", "putting the box down");
        String front = ctx.computeOnClient(mc -> String.valueOf(Tabs.shown().path()));
        if (!front.contains("slot=14")) throw new AssertionError("the box opened again at " + front + ", not where it was put down");
        // A double-click on it, in front, closes it: back to the inventory, the box in its slot.
        // Its first click takes the row's last tab, and this short window's frame moves up as
        // the row goes, out from under the cursor: the second click still counts.
        slowDoubleClick(slot(14), "the open lime box");
        expectScreen(InventoryScreen.class, "double-clicking the open box");
        expectRow("", "double-clicking the open box");
        boolean there = scene.world().getServer().computeOnServer(s -> name(player(s).getInventory().getItem(14)).equals("lime_shulker_box"));
        if (!there) throw new AssertionError("the box isn't back in its slot after the double-click");
    }

    /**
     * Opened by hand, a quick double-click (each press held a tick, a tick apart) on the box in front closes
     * it: back to the inventory, the box in its slot, nothing on the cursor, on both sides. The
     * frame stays put as the row comes and goes.
     */
    private void quickClose() {
        openInventory();
        int before = frameTop();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        // The row came with the box, and the frame stayed where it was (there's room above it).
        if (frameTop() != before) throw new AssertionError("the frame moved from " + before + " to " + frameTop() + " as the box's row came");
        hover(hotbar(0), "the open lime box");
        for (int i = 0; i < 2; i++) {
            ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTick();
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTick();
        }
        ctx.waitTicks(10);
        expectScreen(InventoryScreen.class, "double-clicking the open box");
        String client = ctx.computeOnClient(mc -> name(mc.player.containerMenu.getCarried()) + " " + name(mc.player.getInventory().getItem(0)));
        String server = scene.world().getServer().computeOnServer(s -> name(player(s).containerMenu.getCarried()) + " " + name(player(s).getInventory().getItem(0)));
        if (frameTop() != before) throw new AssertionError("the frame moved from " + before + " to " + frameTop() + " as the row went");
        if (!client.equals("air lime_shulker_box") || !server.equals(client))
            throw new AssertionError("after the double-click the cursor and hotbar 1 read [" + client + "] on the client, [" + server + "] on the server, not [air lime_shulker_box]");
    }

    /**
     * Every box tabbed, the box in front picked up from its own screen: the screen stays, and
     * once the double-click window ends its slots are greyed and locked, the emeralds still
     * shown. Put down elsewhere, they work again, writing to the box where it now is. A
     * double-click on it goes back to the inventory.
     */
    private void everyPickup() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        click(hotbar(0), "the open lime box");
        ctx.waitTicks(3);
        // Till the double-click window ends, the box's screen isn't greyed yet.
        if (ctx.computeOnClient(mc -> Tabs.frozen())) throw new AssertionError("the box's screen greyed with the double-click window still open");
        ctx.waitTicks(25);
        expectScreen(ShulkerBoxScreen.class, "picking up the open box");
        String held = ctx.computeOnClient(mc -> Tabs.frozen() + " " + name(screen(mc).getMenu().getSlot(0).getItem()));
        if (!held.equals("true emerald")) throw new AssertionError("with the box on the cursor its screen reads [" + held + "], not frozen with its emeralds");
        click(slot(14), "an empty inventory slot");
        ctx.waitTicks(3);
        expectScreen(ShulkerBoxScreen.class, "putting the box down");
        if (ctx.computeOnClient(mc -> Tabs.frozen())) throw new AssertionError("the box's slots stayed locked after putting it down");
        shiftClick(s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0, "the emeralds");
        int left = scene.world().getServer().computeOnServer(s -> count(player(s).getInventory().getItem(14), net.minecraft.world.item.Items.EMERALD));
        if (left != 0) throw new AssertionError("the box where it was put down still holds " + left + " emeralds after taking them");
        slowDoubleClick(slot(14), "the open lime box");
        expectScreen(InventoryScreen.class, "double-clicking the box in front");
        boolean there = scene.world().getServer().computeOnServer(s -> name(player(s).getInventory().getItem(14)).equals("lime_shulker_box"));
        if (!there) throw new AssertionError("the box isn't back in its slot after the double-click");
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

    /**
     * The wheel spun fast through the tabs, a turn every tick and several a frame, both ways:
     * each tab's screen swap starts the frame's resize before the last one ends, and nothing
     * throws (the swap's clip once crashed when its animation ran out mid-frame).
     */
    private void fastWheel() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        ctx.getInput().setCursorPos(2, 2);
        java.util.Set<TabId> seen = new java.util.HashSet<>();
        for (int i = 0; i < 60; i++) {
            double way = i / 15 % 2 == 0 ? -1 : 1;
            ctx.getInput().scroll(way);
            if (i % 3 == 0) ctx.getInput().scroll(way);
            ctx.waitTick();
            seen.add(ctx.computeOnClient(mc -> Tabs.shown()));
        }
        ctx.waitTicks(10);
        if (seen.size() < 3) throw new AssertionError("the fast wheel showed only " + seen.size() + " tabs");
        if (ctx.computeOnClient(mc -> !(mc.gui.screen() instanceof AbstractContainerScreen<?>))) throw new AssertionError("the fast wheel left " + ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
    }

    /** With nothing to open, every box tabbed shows the Inventory tab alone; opened by hand, no row. */
    private void inventoryAlone() {
        command("clear @p");
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        expectRow("INVENTORY", "the inventory opened with no boxes, every box tabbed");
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
        ctx.waitTicks(3);
        expectRow("", "opened by hand, with no boxes");
    }

    /**
     * The wheel switches tabs wherever it's turned on the screen: over a slot, a gap in the
     * frame, a tab, and the tab row's strip past its last tab. Each place a turn down and back up.
     */
    private void wheelEverywhere() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        var edge = com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP;
        double[][] places = ctx.computeOnClient(mc -> {
            ScreenAccessor f = (ScreenAccessor) screen(mc);
            var l = com.trevlar.menukit.inject.ContainerTabRows.lineForChecks(edge);
            if (l == null) throw new AssertionError("no tab row on the chest");
            Slot first = screen(mc).getMenu().slots.getFirst();
            var tabs = com.trevlar.menukit.inject.ContainerTabRows.placedForChecks(edge);
            int end = 0;
            for (var p : tabs) end = Math.max(end, p.x() + p.width());
            var tab = tabs.getFirst();
            double rowY = l.rowTop() + l.height() / 2.0;
            return new double[][] {
                    {f.openanywhere$leftPos() + first.x + 8, f.openanywhere$topPos() + first.y + 8},
                    {f.openanywhere$leftPos() + 4, f.openanywhere$topPos() + 4},
                    {tab.x() + tab.width() / 2.0, l.rowTop() + l.height() - tab.height() / 2.0},
                    {end + 12, rowY}};
        });
        String[] names = {"a slot", "a gap in the frame", "a tab", "the strip past the last tab"};
        for (int i = 0; i < places.length; i++) {
            double[] at = places[i];
            ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> at[0] * scale(mc)), ctx.computeOnClient(mc -> at[1] * scale(mc)));
            ctx.waitTick();
            TabId home = ctx.computeOnClient(mc -> Tabs.selected());
            ctx.getInput().scroll(-1);
            ctx.waitTicks(5);
            TabId down = ctx.computeOnClient(mc -> Tabs.selected());
            if (down.equals(home)) throw new AssertionError("the wheel over " + names[i] + " stayed on " + home);
            ctx.getInput().scroll(1);
            ctx.waitTicks(5);
            expectSelected(home, "the wheel back up over " + names[i]);
        }
    }

    /** Every box gets a tab: all of them in reach, in order, the bundle inside the cyan box too; the wheel moves between them. */
    private void everyBox() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        // The inventory has its tabs every time it opens, not only once something moves.
        for (int i = 0; i < 2; i++) {
            openInventory();
            int tabs = ctx.computeOnClient(mc -> Tabs.row().size());
            if (tabs < 2) throw new AssertionError("the inventory opened (time " + (i + 1) + ") with no tabs");
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
        }
        openBlock();
        pick(tabOf("red_shulker_box"));
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
        // A row too long for the screen: twenty more boxes. The tab in front stays in view.
        String[] colours = {"white", "light_gray", "gray", "black", "brown", "yellow", "lime", "pink", "magenta", "blue"};
        for (int i = 0; i < 20; i++) command("item replace entity @p inventory." + (i + 1) + " with minecraft:" + colours[i % 10] + "_shulker_box");
        ctx.waitTicks(10);
        // The inventory's last box: the row's far end while the inventory's boxes are out (a chest's box would slide them in).
        int last = ctx.computeOnClient(mc -> {
            int at = -1;
            for (int i = 0; i < Tabs.row().size(); i++) if (TabId.INVENTORY.equals(Tabs.row().get(i).parent())) at = i;
            return at;
        });
        TabId early = ctx.computeOnClient(mc -> Tabs.row().get(4).id());
        pick(early);
        ctx.waitTicks(10);
        ctx.takeScreenshot("screens-overflow-early");
        // Paged on past it: the inventory's header and the bundle in front stay at the left end.
        for (int k = 0; k < 3; k++) {
            pageArrow(true);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);
            ctx.takeScreenshot("screens-overflow-paged-" + k);
        }
        // From the far end, paged back: the open group's header and the tab in front stay at the right end.
        TabId lastId = ctx.computeOnClient(mc -> Tabs.row().get(last).id());
        pick(lastId);
        ctx.waitTicks(10);
        for (int k = 0; k < 3; k++) {
            pageArrow(false);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);
            ctx.takeScreenshot("screens-overflow-back-" + k);
        }
        for (int i : new int[] {last}) {
            TabId id = ctx.computeOnClient(mc -> Tabs.row().get(i).id());
            pick(id);
            ctx.waitTicks(10);
            ctx.takeScreenshot("screens-overflow-tab-" + i);
        }
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
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
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
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        switchAndWatch(() -> doubleClickNow(containerSlot(4), "the purple box"), "ShulkerBoxScreen", "opening the box");
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(TabId.WORLD)), "ContainerScreen", "back to the chest");
        switchAndWatch(() -> doubleClickNow(hotbar(2), "the bundle"), "ContainerScreen", "opening the bundle");
        TabId box = tabOf("purple_shulker_box");
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(box)), "ShulkerBoxScreen", "the box's tab from the bundle");
        TabId bundle = tabOf("bundle");
        ctx.waitTicks(3);
        switchAndWatch(() -> ctx.runOnClient(mc -> Tabs.pick(bundle)), "ContainerScreen", "the bundle's tab from the box");
    }

    /** A large chest's tab: the world tab is there, and a close shot of its icon, two halves as one chest. */
    private void doubleChest() {
        openBlock();
        expectRow("INVENTORY, WORLD", "the large chest opened");
        int rows = ctx.computeOnClient(mc -> ((net.minecraft.world.inventory.ChestMenu) screen(mc).getMenu()).getRowCount());
        if (rows != 6) throw new AssertionError("the large chest opened with " + rows + " rows, not 6");
        ctx.waitTicks(10);
        // At GUI scale 1, so the taller screen's tabs are on screen in this small window.
        int scale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
        ctx.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeGui(); });
        ctx.waitTicks(3);
        ctx.takeScreenshot("screens-double-chest-tab");
        ctx.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
    }

    /**
     * A placed shulker box opened as the world container: its tab shows the box itself (the
     * block under the crosshair), not the chest every other world container falls back to.
     */
    private void worldShulkerIcon() {
        openWorldShulker();
        expectRow("INVENTORY, WORLD", "the placed box opened");
        String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks()));
        if (!icon.equals("purple_shulker_box"))
            throw new AssertionError("the placed box's tab icon is " + icon + ", not purple_shulker_box");
    }

    /**
     * A placed box the player has named. The screen's title is that name, which the client's
     * copy of the box never hears, so a tab icon guessed from the block under the crosshair
     * drew a chest. The icon is now the server's: the block the screen was opened on.
     */
    private void worldShulkerNamed() {
        BlockPos b = block();
        command(String.format("data merge block %d %d %d {CustomName:\"Ore Box\"}", b.getX(), b.getY(), b.getZ()));
        ctx.waitTicks(3);
        openWorldShulker();
        expectRow("INVENTORY, WORLD", "the named box opened");
        String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks()));
        if (!icon.equals("purple_shulker_box"))
            throw new AssertionError("the named box's tab icon is " + icon + ", not purple_shulker_box");
    }

    /**
     * In both tab modes, a crafting table, a furnace and a stonecutter each get the world tab,
     * apart from the inventory's, showing their block: the Inventory tab goes to the inventory
     * with the station waiting behind, and the station's tab brings it back. Opened by hand is
     * about the boxes inside a container, never whether the container gets its tab.
     */
    private void everyContainer() {
        command("clear @p");
        Object[][] stations = {
                {"crafting_table", net.minecraft.client.gui.screens.inventory.CraftingScreen.class},
                {"furnace", net.minecraft.client.gui.screens.inventory.FurnaceScreen.class},
                {"stonecutter", net.minecraft.client.gui.screens.inventory.StonecutterScreen.class}};
        BlockPos b = block();
        for (Settings.TabMode mode : Settings.TabMode.values()) {
            ctx.runOnClient(mc -> Settings.setTabs(mode));
            for (Object[] s : stations) {
                String id = (String) s[0];
                Class<?> type = (Class<?>) s[1];
                String what = "the " + id + " (" + mode + ")";
                command(String.format("setblock %d %d %d minecraft:%s", b.getX(), b.getY(), b.getZ(), id));
                ctx.waitTicks(3);
                openAt(b, type, what);
                expectRow("INVENTORY, WORLD", what + " opened");
                String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks()));
                if (!icon.equals(id)) throw new AssertionError(what + "'s tab icon is " + icon);
                pick(TabId.INVENTORY);
                expectScreen(InventoryScreen.class, "the Inventory tab at " + what);
                if (!setAside()) throw new AssertionError(what + " isn't waiting behind the inventory");
                pick(TabId.WORLD);
                expectScreen(type, what + "'s tab");
                ctx.runOnClient(mc -> mc.player.closeContainer());
                ctx.waitTicks(5);
            }
        }
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
    }

    /** A vanilla container screen to open from the world: what it is, its screen, and how it opens. */
    private record Station(String what, Class<?> type, Runnable open) {}

    /**
     * Every vanilla container screen, opened from the world with every box tabbed, the tabs
     * work alike: the wheel over its frame moves to the next tab and back (the merchant's,
     * stonecutter's and loom's lists once took every turn), a click on a tab switches to it,
     * the Open key opens a box from its hotbar and the settings key opens the settings and
     * comes back, Escape closes it and it opens again on its tab. Then with the recipe book
     * open on the Inventory tab, its tab shows the frame whole beside the borrowed book (the
     * chest's image once stayed where it was, half under the book, while its slots moved).
     */
    private void everyScreen() {
        command("recipe give @p minecraft:crafting_table");
        BlockPos b = block();
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        List<Station> stations = new ArrayList<>();
        for (Object[] s : new Object[][] {
                {"chest", ContainerScreen.class}, {"barrel", ContainerScreen.class}, {"ender_chest", ContainerScreen.class},
                {"green_shulker_box", ShulkerBoxScreen.class}, {"hopper", net.minecraft.client.gui.screens.inventory.HopperScreen.class},
                {"dispenser", net.minecraft.client.gui.screens.inventory.DispenserScreen.class},
                {"dropper", net.minecraft.client.gui.screens.inventory.DispenserScreen.class},
                {"crafter", net.minecraft.client.gui.screens.inventory.CrafterScreen.class},
                {"crafting_table", net.minecraft.client.gui.screens.inventory.CraftingScreen.class},
                {"furnace", net.minecraft.client.gui.screens.inventory.FurnaceScreen.class},
                {"blast_furnace", net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen.class},
                {"smoker", net.minecraft.client.gui.screens.inventory.SmokerScreen.class},
                {"brewing_stand", net.minecraft.client.gui.screens.inventory.BrewingStandScreen.class},
                {"enchanting_table", net.minecraft.client.gui.screens.inventory.EnchantmentScreen.class},
                {"anvil", net.minecraft.client.gui.screens.inventory.AnvilScreen.class},
                {"grindstone", net.minecraft.client.gui.screens.inventory.GrindstoneScreen.class},
                {"smithing_table", net.minecraft.client.gui.screens.inventory.SmithingScreen.class},
                {"stonecutter", net.minecraft.client.gui.screens.inventory.StonecutterScreen.class},
                {"loom", net.minecraft.client.gui.screens.inventory.LoomScreen.class},
                {"cartography_table", net.minecraft.client.gui.screens.inventory.CartographyTableScreen.class},
                {"beacon", net.minecraft.client.gui.screens.inventory.BeaconScreen.class}}) {
            String id = (String) s[0];
            Class<?> type = (Class<?>) s[1];
            stations.add(new Station("the " + id, type, () -> {
                if (ctx.computeOnClient(mc -> !BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(b).getBlock()).getPath().equals(id))) {
                    command(String.format("setblock %d %d %d minecraft:%s", b.getX(), b.getY(), b.getZ(), id));
                    ctx.waitTicks(3);
                }
                openAt(b, type, "the " + id);
            }));
        }
        stations.add(new Station("the villager", net.minecraft.client.gui.screens.inventory.MerchantScreen.class, () -> {
            command(String.format("tp @p %d %d %d", b.getX(), b.getY() + 1, b.getZ() - 2));
            scene.world().getServer().runOnServer(sv -> {
                var level = player(sv).level();
                var box = new net.minecraft.world.phys.AABB(b).inflate(4);
                var found = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, box, net.minecraft.world.entity.Entity::isAlive);
                if (found.isEmpty()) sv.getCommands().performPrefixedCommand(sv.createCommandSourceStack(), String.format(
                        "summon villager %d %d %d {NoAI:1b,VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:librarian\",level:1}}",
                        b.getX(), b.getY() + 1, b.getZ()));
                var villager = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, box, net.minecraft.world.entity.Entity::isAlive).getFirst();
                player(sv).interactOn(villager, net.minecraft.world.InteractionHand.MAIN_HAND, villager.position());
            });
            ctx.waitTicks(5);
        }));
        stations.add(new Station("the donkey", net.minecraft.client.gui.screens.inventory.HorseInventoryScreen.class, () -> {
            command(String.format("tp @p %d %d %d", b.getX(), b.getY() + 1, b.getZ() - 2));
            scene.world().getServer().runOnServer(sv -> {
                var level = player(sv).level();
                var box = new net.minecraft.world.phys.AABB(b).inflate(4);
                if (level.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Donkey.class, box, net.minecraft.world.entity.Entity::isAlive).isEmpty())
                    sv.getCommands().performPrefixedCommand(sv.createCommandSourceStack(), String.format(
                            "summon donkey %d %d %d {NoAI:1b,Tame:1b,ChestedHorse:1b}", b.getX(), b.getY() + 1, b.getZ()));
                level.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Donkey.class, box, net.minecraft.world.entity.Entity::isAlive)
                        .getFirst().openCustomInventoryScreen(player(sv));
            });
            ctx.waitTicks(5);
        }));

        var open = OpenAnywhereClient.OPEN;
        var settings = OpenAnywhereClient.OPEN_SETTINGS;
        ctx.runOnClient(mc -> {
            open.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_O));
            settings.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_P));
            net.minecraft.client.KeyMapping.resetMapping();
        });
        List<String> borrowed = new ArrayList<>(), broken = new ArrayList<>();
        try {
            for (Station s : stations) {
                if (s.what().equals("the villager")) {
                    command(String.format("setblock %d %d %d minecraft:air", b.getX(), b.getY(), b.getZ()));
                    command(String.format("setblock %d %d %d minecraft:air", b.getX(), b.getY() + 1, b.getZ()));
                }
                // Each screen on its own, so one that fails doesn't hide the rest.
                try {
                    tabsWork(s);
                } catch (AssertionError e) {
                    broken.add(e.getMessage());
                    ctx.runOnClient(mc -> { if (mc.player != null) mc.player.closeContainer(); });
                    ctx.waitTicks(5);
                }
                try {
                    if (bookBeside(s)) borrowed.add(s.what());
                } catch (AssertionError e) {
                    broken.add(e.getMessage());
                }
                command("kill @e[type=!player]");
                ctx.waitTicks(3);
            }
        } finally {
            ctx.runOnClient(mc -> {
                open.setKey(InputConstants.UNKNOWN);
                settings.setKey(InputConstants.UNKNOWN);
                net.minecraft.client.KeyMapping.resetMapping();
            });
            bookOpen(false);
            ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
        }
        for (String need : new String[] {"the chest", "the ender_chest", "the green_shulker_box", "the hopper", "the dispenser"})
            if (!borrowed.contains(need)) broken.add(need + " never showed the borrowed recipe book; it did on " + borrowed);
        if (!broken.isEmpty()) throw new AssertionError(broken.size() + " screens failed:\n    " + String.join("\n    ", broken));
    }

    /** {@code s}'s tabs, by wheel, click, keys, and closing and opening again. */
    private void tabsWork(Station s) {
        s.open().run();
        expectScreen(s.type(), s.what() + " opened");
        TabId home = ctx.computeOnClient(mc -> Tabs.selected());
        // The wheel over the frame's middle: up a tab, then back down from the corner.
        ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> {
            ScreenAccessor f = (ScreenAccessor) screen(mc);
            return (f.openanywhere$leftPos() + f.openanywhere$imageWidth() / 2.0) * scale(mc);
        }), ctx.computeOnClient(mc -> {
            ScreenAccessor f = (ScreenAccessor) screen(mc);
            return (f.openanywhere$topPos() + f.openanywhere$imageHeight() / 2.0) * scale(mc);
        }));
        ctx.waitTick();
        ctx.getInput().scroll(1);
        ctx.waitTicks(5);
        TabId up = ctx.computeOnClient(mc -> Tabs.selected());
        if (up.equals(home)) throw new AssertionError("the wheel over " + s.what() + "'s frame stayed on its tab");
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTick();
        ctx.getInput().scroll(-1);
        expectScreen(s.type(), "the wheel back down to " + s.what());
        expectSelected(home, "the wheel back down to " + s.what());
        // A click on the first tab, the Inventory's, where MenuKit draws it.
        clickTab(0, s.what());
        expectScreen(InventoryScreen.class, "a click on the Inventory tab at " + s.what());
        pick(home);
        expectScreen(s.type(), s.what() + "'s tab picked again");
        // The Open key held over the lime box in the hotbar.
        hover(hotbar(0), "the lime box at " + s.what());
        ctx.getInput().holdKey(OpenAnywhereClient.OPEN);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.getInput().releaseKey(OpenAnywhereClient.OPEN);
        expectScreen(ShulkerBoxScreen.class, "the Open key on the lime box at " + s.what());
        pick(home);
        expectScreen(s.type(), s.what() + "'s tab after the lime box");
        // The settings key, and Escape back.
        ctx.getInput().pressKey(OpenAnywhereClient.OPEN_SETTINGS);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(mc -> s.type().isInstance(mc.gui.screen()) || mc.gui.screen() == null))
            throw new AssertionError("the settings key at " + s.what() + " left " + ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        expectScreen(s.type(), "Escape from the settings at " + s.what());
        // Closed, and opened again on its tab.
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) throw new AssertionError("Escape didn't close " + s.what());
        s.open().run();
        expectScreen(s.type(), s.what() + " opened again");
        TabId again = ctx.computeOnClient(mc -> Tabs.selected());
        if (again.type() != home.type()) throw new AssertionError(s.what() + " opened again on " + again + ", not " + home);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
    }

    /**
     * The recipe book open on the Inventory tab, then {@code s}'s tab: where it borrows the
     * book, its frame is drawn whole at its slots' place, its right edge the frame's grey, not
     * the world behind. Whether it borrowed it.
     */
    private boolean bookBeside(Station s) {
        bookOpen(true);
        try {
            s.open().run();
            TabId home = ctx.computeOnClient(mc -> Tabs.selected());
            pick(TabId.INVENTORY);
            expectScreen(InventoryScreen.class, "the Inventory tab at " + s.what() + " with the book open");
            pick(home);
            expectScreen(s.type(), s.what() + "'s tab with the book open");
            if (ctx.computeOnClient(mc -> com.trevorschoeny.openanywhere.RecipeBookCarry.shownOn(mc.gui.screen()) == null)) return false;
            ctx.getInput().setCursorPos(2, 2);
            ctx.waitTicks(10);
            int[] at = ctx.computeOnClient(mc -> {
                ScreenAccessor f = (ScreenAccessor) screen(mc);
                return new int[] {f.openanywhere$leftPos() + f.openanywhere$imageWidth() - 6, f.openanywhere$topPos() + 6, mc.getWindow().getGuiScaledWidth()};
            });
            java.nio.file.Path shot = ctx.takeScreenshot("screens-book-" + s.what().substring(4));
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(shot.toFile());
                double f = (double) img.getWidth() / at[2];
                int rgb = img.getRGB((int) ((at[0] + 0.5) * f), (int) ((at[1] + 0.5) * f)) & 0xFFFFFF;
                if (rgb != 0xC6C6C6) throw new AssertionError(s.what() + "'s frame beside the borrowed book isn't drawn at its right edge: "
                        + String.format("#%06X", rgb) + " at " + at[0] + "," + at[1]);
            } catch (java.io.IOException e) {
                throw new AssertionError("reading " + shot + ": " + e);
            }
            return true;
        } finally {
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            bookOpen(false);
        }
    }

    /** A click on the tab row's tab {@code index}, where MenuKit draws it; fails if something else is there. */
    private void clickTab(int index, String what) {
        var edge = com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP;
        double[] at = ctx.computeOnClient(mc -> {
            var l = com.trevlar.menukit.inject.ContainerTabRows.lineForChecks(edge);
            if (l == null) throw new AssertionError("no tab row on " + what);
            for (var p : com.trevlar.menukit.inject.ContainerTabRows.placedForChecks(edge)) {
                if (p.index() != index) continue;
                double x = p.x() + p.width() / 2.0, y = l.rowTop() + l.height() - p.height() / 2.0;
                int hit = com.trevlar.menukit.inject.ContainerTabRows.hitForChecks(edge, x, y);
                if (hit != index) throw new AssertionError("tab " + index + " on " + what + " isn't at " + x + "," + y + " (hit " + hit + ")");
                return new double[] {x * scale(mc), y * scale(mc)};
            }
            throw new AssertionError("no tab " + index + " on " + what);
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    private static double scale(Minecraft mc) {
        var w = mc.getWindow();
        return (double) w.getScreenWidth() / w.getGuiScaledWidth();
    }

    /**
     * The beacon (230x219: taller and wider than vanilla's standard 176x166) and the anvil
     * (standard size) each get a correct frame on their world tab, and switching from one to
     * Inventory and back resizes the frame to the right bounds rather than getting stuck at
     * the old screen's size.
     */
    private void wideTallStations() {
        command("clear @p");
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        Object[][] stations = {
                {"beacon", net.minecraft.client.gui.screens.inventory.BeaconScreen.class, 230, 219},
                {"anvil", net.minecraft.client.gui.screens.inventory.AnvilScreen.class, 176, 166}};
        BlockPos b = block();
        for (Object[] s : stations) {
            String id = (String) s[0];
            Class<?> type = (Class<?>) s[1];
            int imageWidth = (int) s[2];
            int imageHeight = (int) s[3];
            command(String.format("setblock %d %d %d minecraft:%s", b.getX(), b.getY(), b.getZ(), id));
            ctx.waitTicks(3);
            openAt(b, type, "the " + id);
            expectRow("INVENTORY, WORLD", "the " + id + " opened");
            expectFrame(imageWidth, imageHeight, "the " + id + "'s own screen");
            pick(TabId.INVENTORY);
            expectScreen(InventoryScreen.class, "the Inventory tab at the " + id);
            expectFrame(176, 166, "the Inventory tab at the " + id);
            pick(TabId.WORLD);
            expectScreen(type, "the " + id + "'s tab");
            expectFrame(imageWidth, imageHeight, "back on the " + id + "'s tab");
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
        }
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
    }

    /**
     * A screen's frame, settled after a tab switch: the size vanilla draws it at ({@code imageWidth}
     * x {@code imageHeight}). Not where it sits: MenuKit's own centring can run a frame a little
     * past its margins, even off the edge of a tiny window, when a tall screen's frame and its
     * rows don't both fit (its {@code FrameCentring} docs say so). That's MenuKit's call, not a
     * bug this check polices. What this mod could get wrong is the size: a switch away and back
     * leaving the frame stuck at the wrong screen's bounds.
     */
    private void expectFrame(int imageWidth, int imageHeight, String what) {
        ctx.waitTicks(5);
        int[] got = ctx.computeOnClient(mc -> {
            var screen = mc.gui.screen();
            var acc = (com.trevorschoeny.openanywhere.mixin.ScreenAccessor) screen;
            return new int[] {acc.openanywhere$imageWidth(), acc.openanywhere$imageHeight()};
        });
        int w = got[0], h = got[1];
        if (w != imageWidth || h != imageHeight)
            throw new AssertionError(what + "'s frame is " + w + "x" + h + ", not " + imageWidth + "x" + imageHeight);
    }

    /**
     * A villager's tab icon is its own head, its biome and profession with it, and a wandering
     * trader's its own; on the client each head's item model loads as a special model (a
     * missing one would draw vanilla's purple cube).
     */
    private void traderHeads() {
        BlockPos b = block();
        command(String.format("summon villager %d %d %d {NoAI:1b,VillagerData:{type:\"minecraft:desert\",profession:\"minecraft:farmer\",level:2}}",
                b.getX(), b.getY() + 1, b.getZ()));
        command(String.format("summon wandering_trader %d %d %d {NoAI:1b}", b.getX() + 2, b.getY() + 1, b.getZ()));
        ctx.waitTicks(3);
        String got = scene.world().getServer().computeOnServer(s -> {
            var level = player(s).level();
            var box = new net.minecraft.world.phys.AABB(b).inflate(4);
            var villager = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, box).getFirst();
            var trader = level.getEntitiesOfClass(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader.class, box).getFirst();
            ItemStack v = Heads.of(villager), t = Heads.of(trader);
            var data = v.get(Heads.VILLAGER);
            String out = v.get(DataComponents.ITEM_MODEL) + " " + (data == null ? "no villager"
                    : data.type().unwrapKey().orElseThrow().identifier().getPath() + "/" + data.profession().unwrapKey().orElseThrow().identifier().getPath())
                    + ", " + t.get(DataComponents.ITEM_MODEL);
            villager.discard();
            trader.discard();
            return out;
        });
        String want = "openanywhere:villager_head desert/farmer, openanywhere:wandering_trader_head";
        if (!got.equals(want)) throw new AssertionError("the traders' icons are " + got + ", not " + want);
        String models = ctx.computeOnClient(mc -> mc.getModelManager().getItemModel(Heads.VILLAGER_HEAD).getClass().getSimpleName()
                + ", " + mc.getModelManager().getItemModel(Heads.TRADER_HEAD).getClass().getSimpleName());
        if (!models.equals("SpecialModelWrapper, SpecialModelWrapper"))
            throw new AssertionError("the heads' item models are " + models + ", not special models");
    }

    /**
     * Trading gets the world tab in both tab modes, showing the villager's own head: the
     * Inventory tab sets the trade aside, and the villager's tab brings it back with its
     * trades, not an empty list (SlotKit 2.2.2 sends them again).
     */
    private void villagerTab() {
        BlockPos b = block();
        command("clear @p");
        for (Settings.TabMode mode : Settings.TabMode.values()) {
            ctx.runOnClient(mc -> Settings.setTabs(mode));
            String what = "the villager (" + mode + ")";
            command(String.format("tp @p %d %d %d", b.getX(), b.getY() + 1, b.getZ() - 2));
            command(String.format("summon villager %d %d %d {NoAI:1b,VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:librarian\",level:1}}",
                    b.getX(), b.getY() + 1, b.getZ()));
            ctx.waitTicks(3);
            scene.world().getServer().runOnServer(s -> {
                // Alive: the last round's, killed, lies dying a second.
                var villager = player(s).level().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        new net.minecraft.world.phys.AABB(b).inflate(4), net.minecraft.world.entity.Entity::isAlive).getFirst();
                player(s).interactOn(villager, net.minecraft.world.InteractionHand.MAIN_HAND, villager.position());
            });
            expectScreen(net.minecraft.client.gui.screens.inventory.MerchantScreen.class, what + " opened");
            expectRow("INVENTORY, WORLD", what + " opened");
            String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks())
                    + " " + OpenAnywhereClient.worldIconForChecks().get(DataComponents.ITEM_MODEL));
            if (!icon.endsWith("openanywhere:villager_head")) throw new AssertionError(what + "'s tab icon is " + icon);
            int offers = trades();
            if (offers == 0) throw new AssertionError(what + " opened with no trades");
            pick(TabId.INVENTORY);
            expectScreen(InventoryScreen.class, "the Inventory tab at " + what);
            if (!setAside()) throw new AssertionError(what + "'s trades aren't waiting behind the inventory");
            pick(TabId.WORLD);
            expectScreen(net.minecraft.client.gui.screens.inventory.MerchantScreen.class, what + "'s tab");
            int back = trades();
            if (back != offers) throw new AssertionError(what + "'s tab came back with " + back + " trades, not " + offers);
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
            command("kill @e[type=villager]");
        }
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
    }

    /** The trades on the client's trading screen. */
    private int trades() {
        return ctx.computeOnClient(mc -> ((net.minecraft.world.inventory.MerchantMenu) screen(mc).getMenu()).getOffers().size());
    }

    /**
     * A mount's inventory gets the world tab in both tab modes, showing the mount's spawn egg:
     * the Inventory tab sets it aside, and the mount's tab brings it back with what's in it.
     */
    private void mountTab() {
        BlockPos b = block();
        command("clear @p");
        for (Settings.TabMode mode : Settings.TabMode.values()) {
            ctx.runOnClient(mc -> Settings.setTabs(mode));
            String what = "the donkey (" + mode + ")";
            command(String.format("tp @p %d %d %d", b.getX(), b.getY() + 1, b.getZ() - 2));
            command(String.format("summon donkey %d %d %d {NoAI:1b,Tame:1b,ChestedHorse:1b}", b.getX(), b.getY() + 1, b.getZ()));
            ctx.waitTicks(3);
            command("item replace entity @e[type=donkey,limit=1] horse.0 with minecraft:diamond");
            scene.world().getServer().runOnServer(s -> player(s).level().getEntitiesOfClass(
                    net.minecraft.world.entity.animal.equine.Donkey.class, new net.minecraft.world.phys.AABB(b).inflate(4),
                    net.minecraft.world.entity.Entity::isAlive)
                    .getFirst().openCustomInventoryScreen(player(s)));
            expectScreen(net.minecraft.client.gui.screens.inventory.HorseInventoryScreen.class, what + " opened");
            expectRow("INVENTORY, WORLD", what + " opened");
            String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks()));
            if (!icon.equals("donkey_spawn_egg")) throw new AssertionError(what + "'s tab icon is " + icon);
            pick(TabId.INVENTORY);
            expectScreen(InventoryScreen.class, "the Inventory tab at " + what);
            if (!setAside()) throw new AssertionError(what + " isn't waiting behind the inventory");
            pick(TabId.WORLD);
            expectScreen(net.minecraft.client.gui.screens.inventory.HorseInventoryScreen.class, what + "'s tab");
            boolean diamond = ctx.computeOnClient(mc -> screen(mc).getMenu().slots.stream()
                    .anyMatch(sl -> !(sl.container instanceof Inventory) && sl.getItem().is(Items.DIAMOND)));
            if (!diamond) throw new AssertionError(what + "'s tab came back without its diamond");
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
            command("kill @e[type=donkey]");
        }
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
    }

    /**
     * A use that threw leaves its note behind (the Use mixins clear it on return). The next
     * screen takes it as its icon, but the one after must not: here a crafting table opened
     * twice with no block clicked shows no stone on its second opening.
     */
    private void staleOpener() {
        command("clear @p");
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        for (int i = 0; i < 2; i++) {
            final int round = i;
            scene.world().getServer().runOnServer(s -> {
                if (round == 0) com.trevorschoeny.openanywhere.Opener.using(new ItemStack(Items.STONE));
                player(s).openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) ->
                        new net.minecraft.world.inventory.CraftingMenu(id, inv), net.minecraft.network.chat.Component.literal("Crafting")));
            });
            ctx.waitTicks(5);
            expectScreen(net.minecraft.client.gui.screens.inventory.CraftingScreen.class, "the crafting screen, round " + i);
            if (round == 1) {
                String icon = ctx.computeOnClient(mc -> name(OpenAnywhereClient.worldIconForChecks()));
                if (icon.equals("stone")) throw new AssertionError("the second crafting screen's tab icon is the stale stone");
            }
            ctx.runOnClient(mc -> mc.player.closeContainer());
            ctx.waitTicks(5);
        }
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.HAND));
    }

    /** Stands two blocks north of the station's placed shulker box and opens it. */
    private void openWorldShulker() {
        openAt(block(), ShulkerBoxScreen.class, "the placed box");
    }

    /** Stands two blocks north of {@code b}, looks at it and uses it: {@code type}'s screen must open. */
    private void openAt(BlockPos b, Class<?> type, String what) {
        command(String.format("tp @p %.1f %d %.1f 0 30", b.getX() + 0.5, b.getY(), b.getZ() - 1.5));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(b);
        ctx.waitTick();
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        if (!ctx.computeOnClient(mc -> type.isInstance(mc.gui.screen()))) throw new AssertionError(what + " didn't open");
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
        if (frames.stream().noneMatch(f -> f.startsWith(screen + ":") && !f.startsWith(screen + ":0|")))
            throw new AssertionError(what + ": " + screen + " never showed filled; frames " + frames);
        for (String f : frames) {
            if (f.startsWith(screen + ":0|")) throw new AssertionError(what + ": a frame drew " + screen + " empty (a flash); frames " + frames);
        }
    }

    /** A frame, as {@code ScreenClass:items}: how many of the screen's own container slots (not the inventory's) show an item. */
    private static String describe(net.minecraft.client.gui.screens.Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return screen.getClass().getSimpleName() + ":-";
        int items = 0;
        for (Slot s : container.getMenu().slots) if (!(s.container instanceof Inventory) && s.isActive() && s.hasItem()) items++;
        return screen.getClass().getSimpleName() + ":" + items;
    }

    /**
     * The tree, every box tabbed, at the chest with the red, orange and cyan boxes (a bundle in
     * the cyan): each container's boxes are its sub-tabs; the selected box is a main tab right
     * after the group it sits in, its own boxes its sub-tabs, and the line up from it stays
     * open; selecting away closes the line. An ender chest item is a box like any other.
     */
    private void treeEvery() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        String start = "INVENTORY WORLD*[red_shulker_box, orange_shulker_box, cyan_shulker_box]";
        openBlock();
        expectTree(start, "the chest opened");
        pick(tabOf("cyan_shulker_box"));
        expectScreen(ShulkerBoxScreen.class, "the cyan box's tab");
        expectTree("INVENTORY WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box*[bundle]]", "the cyan box selected");
        pick(tabIn("cyan_shulker_box", "bundle"));
        expectScreen(ContainerScreen.class, "the bundle in the cyan box");
        expectTree("INVENTORY WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box[bundle*]]", "the bundle in the cyan box selected");
        pick(TabId.INVENTORY);
        expectTree("INVENTORY*[lime_shulker_box, ender_chest, bundle] WORLD", "the inventory selected");
        pick(tabIn("INVENTORY", "ender_chest"));
        expectScreen(ContainerScreen.class, "the ender chest's tab");
        expectTree("INVENTORY[lime_shulker_box, ender_chest*[green_shulker_box], bundle] WORLD", "the ender chest selected");
        pick(TabId.WORLD);
        expectTree(start, "the chest selected again");
        expectChestOpen(true, "the tree walked");
    }

    /**
     * Every box tabbed, a line four tabs deep (the inventory, the ender chest, the green box in
     * it, the bundle in that): a box's own boxes are out only while it holds the tab in front;
     * the inventory and every box with boxes on the line are full size; the rest are small,
     * the selected box too when it holds none; and stepping across to a sibling closes the
     * line below the one left.
     */
    private void sizesDeep() {
        command("item replace entity @p enderchest.4 with minecraft:green_shulker_box[container=[{slot:0,item:{id:\"minecraft:bundle\",count:1,components:{\"minecraft:bundle_contents\":[{id:\"minecraft:stone\",count:4}]}}}]]");
        command("item replace entity @p enderchest.5 with minecraft:red_shulker_box");
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        expectTree("INVENTORY*[lime_shulker_box, ender_chest, bundle]", "the inventory opened");
        expectSizes("B* s s s", "the inventory opened");
        pick(tabIn("INVENTORY", "ender_chest"));
        expectTree("INVENTORY[lime_shulker_box, ender_chest*[green_shulker_box, red_shulker_box], bundle]", "the ender chest selected");
        expectSizes("B s B* s s s", "the ender chest selected");
        pick(tabIn("ender_chest", "green_shulker_box"));
        expectScreen(ShulkerBoxScreen.class, "the green box's tab");
        expectTree("INVENTORY[lime_shulker_box, ender_chest[green_shulker_box*[bundle], red_shulker_box], bundle]", "the green box selected");
        expectSizes("B s B B* s s s", "the green box selected");
        pick(tabIn("green_shulker_box", "bundle"));
        expectScreen(ContainerScreen.class, "the bundle in the green box");
        expectTree("INVENTORY[lime_shulker_box, ender_chest[green_shulker_box[bundle*], red_shulker_box], bundle]", "the bundle in the green box selected");
        expectSizes("B s B B s* s s", "the bundle in the green box selected");
        pick(tabIn("ender_chest", "red_shulker_box"));
        expectScreen(ShulkerBoxScreen.class, "the red box's tab");
        expectTree("INVENTORY[lime_shulker_box, ender_chest[green_shulker_box, red_shulker_box*], bundle]", "the red box selected");
        expectSizes("B s B s s* s", "the red box selected");
        pick(TabId.INVENTORY);
        expectTree("INVENTORY*[lime_shulker_box, ender_chest, bundle]", "back on the inventory");
        expectSizes("B* s s s", "back on the inventory");
    }

    /**
     * The tree, opened by hand: no box has a tab until it's double-clicked; it stays open only
     * while it, or something inside it, is selected. The inventory and the chest stay.
     */
    private void treeHand() {
        openBlock();
        expectTree("INVENTORY WORLD*", "the chest opened");
        doubleClick(containerSlot(4), "the cyan box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the cyan box");
        expectTree("INVENTORY WORLD[cyan_shulker_box*]", "the cyan box opened");
        doubleClick(s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0, "the bundle in the cyan box");
        expectScreen(ContainerScreen.class, "double-click on the bundle in the cyan box");
        expectTree("INVENTORY WORLD[cyan_shulker_box[bundle*]]", "the bundle in the cyan box opened");
        pick(tabOf("cyan_shulker_box"));
        expectTree("INVENTORY WORLD[cyan_shulker_box*]", "the cyan box selected over its bundle");
        pick(TabId.WORLD);
        expectTree("INVENTORY WORLD*", "the chest selected");
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        expectTree("INVENTORY[lime_shulker_box*] WORLD", "the lime box opened from the chest's screen");
        pick(TabId.INVENTORY);
        expectScreen(InventoryScreen.class, "the Inventory tab");
        expectTree("INVENTORY* WORLD", "the inventory selected");
        expectChestOpen(true, "the tree walked");
    }

    // Reads

    /**
     * The row as the player sees it: top main tabs in order, a main tab's boxes in brackets
     * while they're out, a box that's a main tab in place with its own, the selected tab
     * starred ({@code INVENTORY[lime_shulker_box*[bundle], ender_chest] WORLD}). Sizes: {@link #sizes}.
     */
    private String tree() {
        return ctx.computeOnClient(mc -> {
            List<String> mains = new ArrayList<>();
            for (TabId main : Tabs.mains()) if (Tabs.tab(main).parent() == null) mains.add(branch(main));
            return String.join(" ", mains);
        });
    }

    private static String branch(TabId id) {
        List<String> inside = new ArrayList<>();
        if (Tabs.mains().contains(id) && Tabs.holds(id, Tabs.selected())) for (TabId sub : Tabs.subs(id)) inside.add(branch(sub));
        return tabName(id) + (id.equals(Tabs.selected()) ? "*" : "") + (inside.isEmpty() ? "" : "[" + String.join(", ", inside) + "]");
    }

    /**
     * The tabs MenuKit draws, left to right, by size: {@code B} full size, {@code s} small, starred
     * when selected ({@code B* s s B}). Only the ones in view and taking clicks: a group's boxes
     * slid in under it aren't.
     */
    private String sizes() {
        return ctx.computeOnClient(mc -> {
            List<String> out = new ArrayList<>();
            var placed = new ArrayList<>(com.trevlar.menukit.inject.ContainerTabRows.placedForChecks(com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP));
            placed.removeIf(p -> p.index() < 0);
            placed.sort(java.util.Comparator.comparingInt(p -> p.x()));
            for (var p : placed) {
                String size = p.height() == com.trevlar.menukit.api.panel.ContainerTabs.TAB_HEIGHT ? "B"
                        : p.height() == com.trevlar.menukit.api.panel.ContainerTabs.SUB_HEIGHT ? "s" : "?" + p.height();
                out.add(size + (p.shown().selected() ? "*" : ""));
            }
            return String.join(" ", out);
        });
    }

    private void expectSizes(String expected, String after) {
        ctx.waitTicks(8);
        String got = sizes();
        if (!got.equals(expected)) throw new AssertionError("after " + after + " the tabs' sizes read [" + got + "], not [" + expected + "]");
    }

    /** Where the hotbar's first slot draws, from the top of the window. */
    private int hotbarY() {
        return ctx.computeOnClient(mc -> {
            var screen = screen(mc);
            for (Slot s : screen.getMenu().slots)
                if (s.container instanceof Inventory && s.getContainerSlot() == 0)
                    return ((com.trevorschoeny.openanywhere.mixin.ScreenAccessor) screen).openanywhere$topPos() + s.y;
            throw new AssertionError("no hotbar on " + screen);
        });
    }

    /** Where the screen's frame starts, from the top of the window. */
    private int frameTop() {
        return ctx.computeOnClient(mc -> ((com.trevorschoeny.openanywhere.mixin.ScreenAccessor) screen(mc)).openanywhere$topPos());
    }

    private static String tabName(TabId id) {
        return id.type() == TabId.Type.ITEM ? name(Tabs.tab(id).item()) : id.type().name();
    }

    private void expectTree(String expected, String after) {
        ctx.waitTicks(3);
        String tree = tree();
        if (!tree.equals(expected)) throw new AssertionError("after " + after + " the tree reads [" + tree + "], not [" + expected + "]");
    }

    /** The tab for the item named {@code name} in the container whose tab is named {@code parent}. */
    private TabId tabIn(String parent, String name) {
        return ctx.computeOnClient(mc -> {
            for (Tab tab : Tabs.row())
                if (tab.id().type() == TabId.Type.ITEM && tab.parent() != null && name(tab.item()).equals(name) && tabName(tab.parent()).equals(parent)) return tab.id();
            throw new AssertionError("no " + name + " tab in " + parent + " on the row");
        });
    }

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

    /**
     * Every box tabbed, the box in front on the cursor: nothing comes out of or goes into its
     * locked slots, by click, shift-click, a number key or Q. Put down, it holds what it held.
     */
    private void lockedWhileCarried() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        click(hotbar(0), "the open lime box");
        ctx.waitTicks(3);
        Predicate<Slot> emeralds = s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0;
        shiftClick(emeralds, "the emeralds in the carried box");
        hover(emeralds, "the emeralds in the carried box");
        ctx.getInput().pressKey(InputConstants.KEY_Q);
        ctx.getInput().pressKey(InputConstants.KEY_5);
        ctx.waitTicks(3);
        click(emeralds, "the emeralds in the carried box");
        shiftClick(slot(9), "the dirt");
        ctx.waitTicks(5);
        click(slot(14), "an empty slot");
        ctx.waitTicks(5);
        String got = scene.world().getServer().computeOnServer(sv -> {
            ServerPlayer p = player(sv);
            int inBox = count(p.getInventory().getItem(14), Items.EMERALD), loose = 0, dirt = 0;
            for (int i = 0; i < 36; i++) {
                if (p.getInventory().getItem(i).is(Items.EMERALD)) loose += p.getInventory().getItem(i).getCount();
                if (p.getInventory().getItem(i).is(Items.DIRT)) dirt += p.getInventory().getItem(i).getCount();
            }
            int dropped = 0;
            for (var e : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8)))
                if (e.getItem().is(Items.EMERALD)) dropped += e.getItem().getCount();
            return inBox + " in the box, " + loose + " loose, " + dropped + " dropped, " + dirt + " dirt, cursor " + name(p.containerMenu.getCarried());
        });
        if (!got.equals("3 in the box, 0 loose, 0 dropped, 64 dirt, cursor air"))
            throw new AssertionError("after trying to move things through the carried box's slots: " + got);
    }

    /**
     * Every box tabbed, two identical boxes: the one in front, moved, is followed to where it
     * went, not mistaken for its twin that stayed put.
     */
    private void twins() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        command("item replace entity @p inventory.0 with " + LIME);
        ctx.waitTicks(3);
        openInventory();
        doubleClick(slot(9), "the second lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the second lime box");
        click(slot(9), "the open lime box");
        click(slot(20), "an empty slot");
        ctx.waitTicks(5);
        String front = ctx.computeOnClient(mc -> String.valueOf(Tabs.shown().path()));
        if (!front.contains("slot=20,")) throw new AssertionError("the moved box's screen follows " + front + ", not slot 20 where it went");
    }

    /**
     * Every box tabbed, the box in front taken away by something else (a command here, a
     * hopper or another mod in play): its screen goes back to the inventory.
     */
    private void takenAway() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        command("item replace entity @p hotbar.0 with minecraft:air");
        ctx.waitTicks(5);
        expectScreen(InventoryScreen.class, "the open box taken away");
    }

    /**
     * A box from a chest in front, and the player walks out of the chest's reach: the chest
     * closes, and the box's screen goes with it, back to the inventory. Nothing written to
     * the box from then on.
     */
    private void walkedAway() {
        openBlock();
        doubleClick(containerSlot(4), "the purple box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the purple box");
        BlockPos chest = block();
        scene.world().getServer().runOnServer(s -> player(s).teleportTo(chest.getX() + 7.5, chest.getY(), chest.getZ() + 7.5));
        ctx.waitTicks(10);
        expectScreen(InventoryScreen.class, "walking out of the chest's reach");
        String box = ctx.computeOnClient(mc -> String.valueOf(Tabs.shownItem()));
        if (!box.equals("null")) throw new AssertionError("a box tab is still in front after walking away: " + box);
    }

    /**
     * A box from a chest in front, and a hopper takes it out of the chest: vanilla's
     * {@code removeItem} empties the box's stack in place, leaving the same object in the
     * slot. Its screen goes, back to the chest; left open, whatever was put in it would be
     * written into a box that is no longer there.
     */
    private void hopperTook() {
        openBlock();
        doubleClick(containerSlot(4), "the purple box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the purple box");
        ItemStack taken = scene.world().getServer().computeOnServer(s -> chest(s).removeItem(4, 1));
        try {
            expectScreen(ContainerScreen.class, "a hopper taking the open box out of the chest");
        } finally {
            scene.world().getServer().runOnServer(s -> chest(s).setItem(4, taken));
        }
    }

    /**
     * Two ender chests carried, the one in front lifted and put down in another slot: its tab
     * comes back selected on the row, though the other ender chest now comes first.
     */
    private void twoEnderChests() {
        command("item replace entity @p hotbar.7 with minecraft:ender_chest");
        ctx.waitTicks(3);
        openInventory();
        doubleClick(hotbar(1), "the first ender chest");
        click(hotbar(1), "the open ender chest");
        click(slot(20), "an empty slot");
        ctx.waitTicks(5);
        boolean onRow = ctx.computeOnClient(mc -> Tabs.tab(Tabs.selected()) != null && Tabs.selected().type() == TabId.Type.ITEM);
        if (!onRow) throw new AssertionError("the moved ender chest's tab isn't on the row: " + rowNames() + ", selected " + ctx.computeOnClient(mc -> String.valueOf(Tabs.selected())));
    }

    /** The player's inventory slot {@code i} (0 to 8 the hotbar, 9 to 35 the rest). */
    private static Predicate<Slot> slot(int i) {
        return s -> s.container instanceof Inventory && s.getContainerSlot() == i && s.isActive();
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

    /** The cursor on the tab row's paging arrow, right or left, where MenuKit draws it; fails if it isn't the arrow. */
    private void pageArrow(boolean right) {
        var edge = com.trevlar.menukit.api.panel.ContainerTabs.Edge.CONTAINERS_TOP;
        double[] at = ctx.computeOnClient(mc -> {
            var l = com.trevlar.menukit.inject.ContainerTabRows.lineForChecks(edge);
            if (l == null) throw new AssertionError("no tab row on " + mc.gui.screen());
            int aw = com.trevlar.menukit.inject.TabRowLayout.ARROW_WIDTH;
            double x = right ? l.x() + l.width() - aw / 2.0 : l.x() + aw / 2.0;
            double y = l.rowTop() + l.height() / 2.0;
            int hit = com.trevlar.menukit.inject.ContainerTabRows.hitForChecks(edge, x, y);
            int want = right ? com.trevlar.menukit.inject.TabRowLayout.FORWARD : com.trevlar.menukit.inject.TabRowLayout.BACK;
            if (hit != want) throw new AssertionError("the " + (right ? "forward" : "back") + " arrow isn't at " + x + "," + y + " (hit " + hit + ")");
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {x * f, y * f};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
    }

    /** One press and release on {@code p}'s slot, two ticks before the next: faster than the server answers. */
    private void pressNow(Predicate<Slot> p, int button, String what) {
        hover(p, what);
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int action : new int[] {1, 0})
                mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(button, 0), action);
        });
    }

    private void rightClick(Predicate<Slot> p, String what) {
        hover(p, what);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
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

    /** A double-click at a hand's pace: two left presses four ticks apart, the cursor still. */
    private void slowDoubleClick(Predicate<Slot> p, String what) {
        doubleClickApart(p, what, 4);
        ctx.waitTicks(4);
    }

    /** A double-click with its presses {@code ticks} apart, the cursor still. */
    private void doubleClickApart(Predicate<Slot> p, String what, int ticks) {
        hover(p, what);
        for (int i = 0; i < 2; i++) {
            ctx.runOnClient(mc -> {
                var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
                for (int action : new int[] {1, 0})
                    mouse.invokeOnButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
            });
            ctx.waitTicks(ticks);
        }
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
