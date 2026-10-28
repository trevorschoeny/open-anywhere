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
import com.trevorschoeny.openanywhere.ContainerHost;
import com.trevorschoeny.openanywhere.Groups;
import com.trevorschoeny.openanywhere.OpenC2S;
import com.trevorschoeny.openanywhere.Openable;
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
            run("openanywhere:screens_box_edits", this::boxEdits);
            run("openanywhere:screens_from_inventory", this::fromInventory);
            run("openanywhere:screens_order", this::order);
            run("openanywhere:screens_ender", this::ender);
            run("openanywhere:screens_ender", this::carriedEnder);
            run("openanywhere:screens_ender", this::carriedEnderTabs);
            run("openanywhere:screens_ender", this::carriedMoved);
            run("openanywhere:screens_ender", this::carriedBox);
            run("openanywhere:screens_bundle", this::bundle);
            run("openanywhere:screens_bundle", this::bundleHalf);
            run("openanywhere:screens_nested", this::nested);
            run("openanywhere:screens_cursor", this::cursor);
            run("openanywhere:screens_lock", this::handPickup);
            run("openanywhere:screens_lock", this::everyPickup);
            run("openanywhere:screens_lock", this::quickClose);
            run("openanywhere:screens_lock", this::lockedWhileCarried);
            run("openanywhere:screens_lock", this::twins);
            run("openanywhere:screens_lock", this::takenAway);
            run("openanywhere:screens_lock", this::twoEnderChests);
            run("openanywhere:screens_box_edits", this::walkedAway);
            run("openanywhere:screens_escape", this::escape);
            run("openanywhere:screens_every_box", this::everyBox);
            run("openanywhere:screens_every_box", this::inventoryAlone);
            run("openanywhere:screens_every_box", this::treeEvery);
            run("openanywhere:screens_every_box", this::treeHand);
            run("openanywhere:screens_no_flash", this::noFlash);
            run("openanywhere:screens_double_chest", this::doubleChest);
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
     * A chest opens on its own tab beside the inventory's, in front from its first frame, its
     * group open; a box in it opens on vanilla's shulker box screen as a third.
     */
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
     * Every box tabbed: each container's boxes are its sub-tabs, by where they sit, not the
     * order they opened. The wheel walks the same line: up from the chest is the inventory's
     * last box, down from the chest is into its first, which then stands as a main tab.
     */
    private void order() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        ctx.waitTicks(3);
        openBlock();
        expectTree("INVENTORY[lime_shulker_box, ender_chest, bundle] WORLD[red_shulker_box, orange_shulker_box]", "the chest opened");
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
        expectTree("INVENTORY[lime_shulker_box, ender_chest, bundle] WORLD[red_shulker_box[], orange_shulker_box]", "the wheel down from the chest");
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
     * opened, then a group tab after the inventory's that stays open when something else is
     * selected, its lid up on the tab and in its slot alike.
     */
    private void carriedEnderTabs() {
        ctx.runOnClient(mc -> Settings.setTabs(Settings.TabMode.EVERY));
        command("item replace entity @p hotbar.1 with minecraft:air");
        pocket(new ItemStack(Items.ENDER_CHEST));
        openInventory();
        expectTree("INVENTORY[lime_shulker_box, bundle, ender_chest]", "an ender chest in the carried slot, not yet opened");
        pick(tabOf("ender_chest"));
        expectScreen(ContainerScreen.class, "the carried ender chest's tab");
        expectTree("INVENTORY[lime_shulker_box, bundle] ender_chest[green_shulker_box]", "the carried ender chest opened");
        pick(TabId.INVENTORY);
        expectTree("INVENTORY[lime_shulker_box, bundle] ender_chest[green_shulker_box]", "the inventory selected after the carried ender chest");
        String look = ctx.computeOnClient(mc -> {
            for (Slot slot : mc.player.containerMenu.slots) {
                ItemPath path = ItemSources.pathOf(mc.player, slot);
                if (path != null && path.source() == ItemRef.Source.CARRIED)
                    return Tabs.looksOpen(slot) + " " + Tabs.open(TabId.item(path));
            }
            return "no carried slot";
        });
        if (!look.equals("true true")) throw new AssertionError("the carried ender chest, open behind the inventory: slot open, tab open = " + look);
        pick(tabOf("green_shulker_box"));
        expectTree("INVENTORY[lime_shulker_box, bundle] ender_chest[green_shulker_box[]]", "the green box in the ender chest selected");
        pick(TabId.INVENTORY);
        expectTree("INVENTORY[lime_shulker_box, bundle] ender_chest[green_shulker_box]", "back on the inventory from the green box");
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

    /** The box in front can't be lifted out from under its own screen. */
    /**
     * Opened by hand, the box in front picked up from its own screen: once the double-click
     * window ends, its tab closes and the screen goes back to the inventory. Put down in another slot, it opens again there, in front.
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
     * once the double-click window ends its slots are greyed and locked, the emeralds still shown. Put down elsewhere, they work again,
     * writing to the box where it now is. A double-click on it goes back to the inventory.
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
        int last = ctx.computeOnClient(mc -> Tabs.row().size()) - 1;
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
        String start = "INVENTORY[lime_shulker_box, ender_chest, bundle] WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box]";
        openBlock();
        expectTree(start, "the chest opened");
        pick(tabOf("cyan_shulker_box"));
        expectScreen(ShulkerBoxScreen.class, "the cyan box's tab");
        expectTree("INVENTORY[lime_shulker_box, ender_chest, bundle] WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box[bundle]]", "the cyan box selected");
        pick(tabIn("cyan_shulker_box", "bundle"));
        expectScreen(ContainerScreen.class, "the bundle in the cyan box");
        expectTree("INVENTORY[lime_shulker_box, ender_chest, bundle] WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box[bundle[]]]", "the bundle in the cyan box selected");
        pick(TabId.INVENTORY);
        expectTree(start, "the inventory selected");
        pick(tabIn("INVENTORY", "ender_chest"));
        expectScreen(ContainerScreen.class, "the ender chest's tab");
        expectTree("INVENTORY[lime_shulker_box, ender_chest[green_shulker_box], bundle] WORLD[red_shulker_box, orange_shulker_box, cyan_shulker_box]", "the ender chest selected");
        pick(TabId.WORLD);
        expectTree(start, "the chest selected again");
        expectChestOpen(true, "the tree walked");
    }

    /**
     * The tree, opened by hand: no box has a tab until it's double-clicked; it stays open only
     * while it, or something inside it, is selected. The inventory and the chest stay.
     */
    private void treeHand() {
        openBlock();
        expectTree("INVENTORY WORLD", "the chest opened");
        doubleClick(containerSlot(4), "the cyan box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the cyan box");
        expectTree("INVENTORY WORLD[cyan_shulker_box[]]", "the cyan box opened");
        doubleClick(s -> !(s.container instanceof Inventory) && s.getContainerSlot() == 0, "the bundle in the cyan box");
        expectScreen(ContainerScreen.class, "double-click on the bundle in the cyan box");
        expectTree("INVENTORY WORLD[cyan_shulker_box[bundle[]]]", "the bundle in the cyan box opened");
        pick(tabOf("cyan_shulker_box"));
        expectTree("INVENTORY WORLD[cyan_shulker_box[]]", "the cyan box selected over its bundle");
        pick(TabId.WORLD);
        expectTree("INVENTORY WORLD", "the chest selected");
        doubleClick(hotbar(0), "the lime box");
        expectScreen(ShulkerBoxScreen.class, "double-click on the lime box");
        expectTree("INVENTORY[lime_shulker_box[]] WORLD", "the lime box opened from the chest's screen");
        pick(TabId.INVENTORY);
        expectScreen(InventoryScreen.class, "the Inventory tab");
        expectTree("INVENTORY WORLD", "the inventory selected");
        expectChestOpen(true, "the tree walked");
    }

    // Reads

    /**
     * The row as its tree: top main tabs in order, each with what's in it in brackets, an open
     * box in place with its own brackets, empty or not ({@code INVENTORY[lime_shulker_box[], ender_chest] WORLD}).
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
        if (Tabs.open(id)) for (TabId sub : Tabs.subs(id)) inside.add(branch(sub));
        // An open box shows its brackets even when empty, so it reads open; the top tabs only when full.
        boolean brackets = !inside.isEmpty() || Tabs.open(id) && id.type() == TabId.Type.ITEM;
        return tabName(id) + (brackets ? "[" + String.join(", ", inside) + "]" : "");
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
