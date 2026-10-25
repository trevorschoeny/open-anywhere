# Open Anywhere

Open Anywhere lets you open shulker boxes, bundles, and your ender chest right where they sit, in your inventory or in an open chest, without placing anything down. Each one gets a tab along the top of the screen, like the creative inventory's, and clicking it opens that box on its own screen.

I kept it separate from Inventory Plus on purpose. Some servers count opening containers from the inventory as cheating, and as its own mod it's easy for them to leave out. It has to be installed on the server as well as the client. In singleplayer, your game is both.

### Features

#### Tabs

- Every shulker box, bundle, and ender chest you carry, or that's in the chest you have open, gets a tab.
- The tabs are grouped under your inventory, the chest you opened, and your ender chest. Click a group's header to see its screen, and its boxes slide out beside it as smaller tabs.
- Click a tab, or scroll the mouse wheel, to move between screens. The chest stays open in the world the whole time.
- If you'd rather keep the screen as vanilla has it, a setting gives tabs only to the boxes you double-click open.

#### Real screens

- A shulker box opens on the shulker box screen and your ender chest on the ender chest screen, so shift-click, number keys, dragging, and double-click to collect all work as usual.
- The inventory tab is your real inventory screen, with crafting and armor.
- The box you're looking at opens its lid in its tab and in its slot.
- Every change is written straight into the box, so closing the screen never loses anything.

#### Bundles

- An opened bundle shows one slot for each stack it holds, plus a slot marked with a plus for adding more.
- A slot takes more of its own item while the bundle has room.
- A bundle with more stacks than fit on screen scrolls.

#### Double-click or a key

- Double-click a box to go to its tab, and double-click it again to go back.
- You can also set a key that opens a box with one click while you hold the key down. It isn't bound to anything until you set it.

#### With Inventory Plus

- Inventory Plus's Sort button sorts an opened shulker box, ender chest, or bundle.
- The settings move to an Open Anywhere tab in Inventory Plus's settings menu.

### Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MenuKit](https://modrinth.com/mod/menukit) 7.4.0 or newer, below 8.0.0, which draws the tabs and the bundle's screen, and moves the frame between screens.
- [SlotKit](https://modrinth.com/mod/slotkit) 2.1.0 or newer, below 3.0.0, which switches between screens while the chest stays open, writes every change back into the box, and checks that a joining player has the mod.
- [Keybindery](https://modrinth.com/mod/keybindery) 1.3.0 or newer, below 2.0.0, which lets the Open key be a combination like Ctrl+X and puts its key button in the settings.

### Pairs well with

- [Inventory Plus](https://modrinth.com/mod/inventory-plus-trevlar), my mod for restocking, sorting, and locking slots. With it, opened boxes and bundles can be sorted and Open Anywhere gets a settings tab. Open Anywhere needs Inventory Plus 3.3.0 or newer, and older versions can't be installed alongside it.

### Compatibility

It runs on Minecraft 26.3 with Fabric. Install it on both the client and the server. A server with Open Anywhere turns away players who don't have it, with a message naming the mods to install. On a server without it, nothing changes and double-clicking does what it does in vanilla.

Inventory Profiles Next's sort works on opened boxes, bundles, and the inventory under them.

### How to use

#### The tabs

Open your inventory with a shulker box, bundle, or ender chest in it, or open a chest, barrel, double chest, ender chest, or chest boat. A row of tabs appears along the top of the screen.

- The tabs come in up to three groups. Inventory has your own head as its icon, the chest you opened has the chest's block, and Ender Chest shows while you carry an ender chest or are at a placed one.
- Each group has a header tab, then a smaller tab for each box in it. Your inventory's boxes come in slot order, hotbar first. A box inside a box in the chest belongs to the chest's group, however deep it sits.
- The group with the tab you're looking at is open, with its boxes slid out beside its header. The other groups show only their header.
- Click a header to show its screen: your inventory, the chest, or your ender chest. Click a box's tab to open that box.
- Hover a tab to see the item's name.
- Scroll the mouse wheel to move one tab left or right. Over a container that scrolls, the wheel scrolls it instead.
- When the tabs don't fit, arrows at the ends page through them. The open group's header and the tab you're on stay in view.
- The chest stays open in the world, lid up, while you move between tabs. Leaving the inventory tab gives back what's in its crafting grid, the same as closing the inventory.
- The screen's frame grows or shrinks to the next screen's size as you switch.
- On a game window too short for a chest's screen and its tabs, the chest's rows scroll inside its frame.

#### Tab modes

The Tabs setting decides which boxes get a tab.

| Tabs | Which boxes have tabs | Groups |
| --- | --- | --- |
| Only boxes I open | The ones you double-click open | The one you're on is open |
| Every box (the default) | Every box, bundle, and ender chest in reach | The one you're on is open |
| Every box, every group open | Every box, bundle, and ender chest in reach | All open |

With Only boxes I open, a close button on the frame's top-right corner closes the box you're looking at.

#### Double-click

Double-click a shulker box, bundle, or ender chest in your inventory or in the chest.

- Keep the mouse still between the two clicks. They have to land within one second.
- With Every box, a double-click shows the box's tab. With Only boxes I open, it opens the box as a new tab.
- Double-click the box you're looking at to go back to its group's screen. With Only boxes I open, that closes its tab too.
- Shift+double-click is still vanilla's move-all.
- A stack of shulker boxes doesn't open, so double-clicking it still collects them. A stack of ender chests opens your ender chest.
- A bundle inside a box in the chest opens too, as its own tab.
- Only one player at a time can have a given box open.
- Furnaces, crafting tables, villagers, and other screens that aren't chests don't open anything.

#### The Open key

The Open key isn't bound to anything by default. Set it in Controls, or in the settings. It can be a single key or a combination like Ctrl+X. Hold it and click an item to open it with one click.

#### On a box's screen

- A shulker box is on the shulker box screen and your ender chest on the ender chest screen. Shift-click works as it does there in vanilla.
- Everything you put in or take out is written into the box right away.
- Shulker boxes can't go inside a shulker box, the same as in vanilla.
- A box that another mod gives more slots, like a Shulker Palette box with extra rows, shows all of them. Anything stored past a box's room is kept.
- The box you're looking at plays its open animation in its tab and in its slot. With Animate opened boxes off, it's grayed in its slot instead.

#### Bundles

A bundle opens on a screen of its own, above your inventory.

- Each stack in the bundle has its own slot, in the bundle's order. The slot after them, marked with a plus, adds to the bundle.
- The bundle holds what a bundle normally holds, by vanilla's weight rule.
- Take a stack from any slot. A filled slot takes more of its own item while the bundle has room, and a different item swaps with it if the new item fits.
- Shift-click an item in your inventory to put it in the bundle, or a stack in the bundle to send it back to your inventory.
- When there are more stacks than fit, the slots scroll, eight to a row so the screen keeps its width.

#### Picking up an open box

You can pick up the box you're looking at, even from under its own screen.

- With Every box, the box's screen stays up, its slots grayed and locked, showing what the box holds. Put the box down anywhere in reach and they work again.
- With Only boxes I open, its tab closes, and the screen goes back to its group's. Put it straight down anywhere in reach and it opens again. If it leaves your cursor any other way, such as being dropped or the screen closing, it stays closed.
- A box moved any other way, by a shift-click, a sort, or Inventory Profiles Next, keeps its screen, which follows it to its new slot.
- A box taken out of reach, by a hopper, a command, or another mod, sends its screen back to its group's.

#### Creative

In creative, double-click a box on the Survival Inventory tab. It opens on its own screen in place of creative's, and its Inventory tab goes back to creative's screen. Nothing opens from the item tabs.

#### Settings

Open the settings from Mod Menu's config button, if Mod Menu is installed, or with the Open Settings key, which is unbound by default. Without Inventory Plus, this opens a settings screen of Open Anywhere's own. With Inventory Plus installed, it opens an Open Anywhere tab in Inventory Plus's settings menu instead. The settings are saved in `config/openanywhere/settings.json`.

| Setting | Default |
| --- | --- |
| Open shulker boxes | On |
| Open bundles | On |
| Open the ender chest | On |
| Double-click to open | On |
| Open key (hold and click) | No key |
| Tabs | Every box |
| Scroll to switch tabs | On |
| Animate opened boxes | On |

- Turning a kind off stops it from opening.
- With Double-click to open off, only the Open key opens things.
- Reset to Defaults resets Open Anywhere's settings, on its own screen and on the Inventory Plus tab alike.
- Reset everything, in Inventory Plus only, resets every Inventory Plus setting, Open Anywhere's included.

### For mod developers

Other mods can read which opened item a player is looking at, change it on the server, give shulker boxes more slots, and place their own panel beside the opened item's screen.

#### What the API provides

The API is in the `com.trevorschoeny.openanywhere.api` package.

- `OpenItems.shown(player)` is the opened item whose screen the player has in front, or null when their screen is something else. On the server the stack is the item itself, so a change made in place is kept and written back to the slot it's in. On the client it's a copy for reading. Check it on the server before acting on a request about the open item.
- `OpenItems.boxSize(size)` is for a mod that gives shulker boxes more than 27 slots. An opened box shows every slot the largest answer gives, and anything stored past a box's size is kept.
- `OpenItems.Box` is the container behind an opened box's screen on the server, and its `stack()` is the box item, live.
- `ShownItem.get()`, on the client, returns the opened item in front, or null.
- `ShownItem.SECTION` is a MenuKit anchor on the frame the opened item shows in, for a panel placed beside it. It's absent while no opened item is in front, so the panel hides with it.

#### Example

Shulker Palette gives an opened palette box room for all its rows, and places its buttons beside the box's screen:

```java
OpenItems.boxSize(stack -> Palette.isShulker(stack) ? Palette.MAX_ROWS * Palette.ROW_WIDTH : 0);
```

```java
Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":open_anywhere")
        .in(ModeScope.BOTH, Placement.around(ShownItem.SECTION, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
        .elements(List.of(Flow.builder().vertical().gap(0, 2).addAll(PaletteControls.icons(SHOWN)).build()))
        .build();
```

On the server, it checks the item in front before changing it:

```java
OpenItems.OpenItem item = OpenItems.shown(player);
if (item == null || !Palette.isShulker(item.stack())) return null;
```

#### Getting started

Open Anywhere is one jar, with no separate API module. Compile against it with `compileOnly`, and list it under `suggests` in your `fabric.mod.json` unless your mod can't work without it. The API is documented in the Javadoc of the [source code](https://github.com/trevorschoeny/open-anywhere).

### License

MIT.
