## 1.2.0

#### Added

- Crafting tables, furnaces, smokers, blast furnaces, brewing stands, anvils, grindstones, stonecutters, looms, hoppers, and the other block screens get tabs, in both tab modes. The block you opened gets its own tab next to the Inventory tab. With Only boxes I open, it's there before you open a box inside it.
- Villager trading gets a tab, showing the head of the villager you're trading with, in its own biome and profession, turning to face you like the Inventory tab's head does. A wandering trader shows its own head. Switch away and back and the trades are still there.
- Horse, donkey, mule, llama, camel, and nautilus inventories get a tab, showing the animal's spawn egg.
- The mouse wheel moves tabs on every container screen, including trading, the stonecutter, and the loom, not only chests.
- The recipe book stays open moving between tabs and boxes instead of closing, and a screen opened from the world starts with it open if it was open on the Inventory tab.
- A tab sizing rule holds at every depth: the top tabs are always full size; a box's tab is full size only while it's on the line to the selected tab and has boxes of its own showing, small otherwise; a box's children stay hidden until it's selected.

#### Fixed

- A placed shulker box's tab shows the box in its colour, even after you've named it, instead of a chest.
- A bundle holding more than 65 stacks crashed the server on load; it now keeps the extra items and writes them back.
- A tab click the server refused left the wrong tab highlighted and threw off the scroll wheel's count.

#### Changed

- Requires MenuKit 7.5.1 or newer and SlotKit 2.3.0 or newer.

## 1.1.0

#### Added

- Shulker boxes, bundles, and ender chests in other mods' carried slots, such as Pocket Ender Chest's ender chest slot, open and get tabs like the ones in your inventory.
- With Pocket Ender Chest, its ender chest's tab and its slot always show the same lid. With Every box, its tab stays once you've opened it, until you close the screen.

#### Changed

- The tab row follows what's open. Your inventory and the chest keep their tabs, and an opened box's tab sits right after the container it's in, with any box opened inside it after that.
- Only boxes I open is the new default. A box gets a tab when you double-click it, and the tab closes once neither it nor anything inside it is selected.
- With Every box, each closed box is a small tab under the container holding it, and the box you're on becomes a full tab in its place. The Every box, every group open setting is gone, and anyone using it now has Every box.
- The close button on the frame is gone, since another tab is always there to go to.
- Double-click works the same way as in Pocket Ender Chest and other mods that share the gesture.
- Picking up an open box waits out the double-click window, as any lifted item does: its tab or screen stays as it is until the window passes, so a second click on it closes it cleanly as a double-click.
- With Only boxes I open, the inventory or chest no longer jumps position as tabs appear or go away, including when a bundle opens; it moves only as far as needed when the row wouldn't fit.
- Requires MenuKit 7.5.0 or newer and SlotKit 2.2.0 or newer.
