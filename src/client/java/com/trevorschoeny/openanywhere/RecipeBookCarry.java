package com.trevorschoeny.openanywhere;

import com.trevorschoeny.openanywhere.mixin.RecipeBookAccessor;
import com.trevorschoeny.openanywhere.mixin.RecipeBookScreenAccessor;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * The recipe book across tabs, so the frame doesn't slide away from it and back.
 *
 * <ul>
 *   <li><b>Between two screens with a book</b> (the inventory, a crafting table, a furnace):
 *       the new screen's book is open if the last one's was, closed if it wasn't. Vanilla keeps
 *       one open switch per kind of book, so the furnace's would otherwise differ from the
 *       inventory's.</li>
 *   <li><b>On a screen without one</b> (a box, a chest, the bundle's host, a hopper): the last
 *       screen's open book stands beside the frame, the same object, so its page, recipe tab
 *       and search come along, and the frame keeps its left edge where a book screen's is. Not
 *       on a window too narrow for the book beside the frame (vanilla lays it over the frame
 *       there, and nothing slides), nor where the frame wouldn't fit beside it (the beacon on a
 *       small window).</li>
 *   <li><b>From the world with the inventory's book open:</b> the first screen starts as if
 *       the Inventory tab had just been left with it open, so a book screen's book is open and
 *       a screen without one borrows a fresh inventory book, and switching to the Inventory
 *       tab slides nothing. With it closed, the first screen opens as vanilla's.</li>
 *   <li><b>A recipe picked</b> on a borrowed book: the book's own tab comes to the front (the
 *       Inventory tab, or the world tab's crafting table or furnace) and the recipe is placed
 *       there once its screen is up, so the player sees the grid fill.</li>
 * </ul>
 *
 * <p>A tab session's state only: it starts over each time the screens open from the world. Render thread only.
 */
public final class RecipeBookCarry {

    private RecipeBookCarry() {}

    /** Below this window width vanilla lays the book over the frame rather than beside it. */
    private static final int NARROW = 379;
    /** The frame width vanilla's book screens have: a frame of any width keeps their left edge. */
    private static final int BOOK_SCREEN_WIDTH = 176;

    // Whether the book was open on the last screen with a book left this session; null before one is left.
    private static @Nullable Boolean open;
    // That screen's book while open, borrowed by the screens without one; null when closed.
    private static @Nullable RecipeBookComponent<?> book;
    // The book's kind, its tab, and how much taller than the window it was laid out
    // (MenuKit centring the frame with a tab row reads the window taller), so it stays put.
    private static RecipeBookType type = RecipeBookType.CRAFTING;
    private static TabId home = TabId.INVENTORY;
    private static int taller;
    // The screen the book is borrowed by now.
    private static @Nullable Screen borrower;
    // A recipe picked on the borrowed book, placed once the book's own screen is up.
    private record Pending(RecipeBookType type, RecipeDisplayId recipe, boolean max) {}
    private static @Nullable Pending pending;

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs) || !Tabs.isTabScreen(acs)) return;
            ScreenEvents.remove(screen).register(RecipeBookCarry::left);
            // Landed on the picked recipe's screen: place it, as a click on its own book would.
            Pending p = pending;
            if (p != null && acs instanceof AbstractRecipeBookScreen<?> && acs.getMenu() instanceof net.minecraft.world.inventory.RecipeBookMenu menu) {
                pending = null;
                if (menu.getRecipeBookType() == p.type()) client.gameMode.handlePlaceRecipe(acs.getMenu().containerId, p.recipe(), p.max());
            }
        });
    }

    /** A tab screen taken down: what its book was, for the next one. */
    private static void left(Screen screen) {
        if (screen instanceof AbstractRecipeBookScreen<?> rb) {
            RecipeBookComponent<?> own = ((RecipeBookScreenAccessor) rb).openanywhere$book();
            open = own.isVisible();
            book = open ? own : null;
            type = rb.getMenu().getRecipeBookType();
            home = rb.getMenu() instanceof InventoryMenu ? TabId.INVENTORY : TabId.WORLD;
            taller = ((RecipeBookAccessor) own).openanywhere$height() - screen.height;
        } else if (book != null && !book.isVisible()) {
            book = null;
            open = false;
        }
    }

    /**
     * At the end of {@code screen}'s {@code AbstractContainerScreen.init}, before its own class
     * places anything from {@code leftPos}: on its {@code first} init a book screen's book is
     * set open or closed to match the last one's; a screen without one borrows the open book.
     * The frame's new {@code leftPos} when it borrows it, else -1.
     */
    public static int initialised(AbstractContainerScreen<?> screen, int imageWidth, boolean first) {
        if (borrower == screen) borrower = null;
        if (open == null && first && Tabs.isTabScreen(screen)) startFromInventory();
        if (open == null || !Tabs.isTabScreen(screen) || screen.width < NARROW) return -1;
        if (screen instanceof AbstractRecipeBookScreen<?> rb) {
            if (first) Minecraft.getInstance().player.getRecipeBook().setOpen(rb.getMenu().getRecipeBookType(), open);
            return -1;
        }
        if (book == null) return -1;
        // Laid out first, as vanilla's book screens do: a fresh book isn't visible until then, so it would place nothing.
        book.init(screen.width, screen.height + taller, Minecraft.getInstance(), false);
        if (!book.isVisible()) return -1;
        int left = book.updateScreenPosition(screen.width, BOOK_SCREEN_WIDTH);
        if (left + imageWidth > screen.width) return -1;
        borrower = screen;
        return left;
    }

    /**
     * A session's first screen with the inventory's book open: carry it as if the Inventory
     * tab had just been left. Closed, nothing is carried, so a furnace keeps its own book's switch.
     */
    private static void startFromInventory() {
        var player = Minecraft.getInstance().player;
        if (!player.getRecipeBook().isOpen(RecipeBookType.CRAFTING)) return;
        open = true;
        book = new CraftingRecipeBookComponent(player.inventoryMenu);
        type = RecipeBookType.CRAFTING;
        home = TabId.INVENTORY;
        // Assumes the inventory's frame sits at vanilla's place. If MenuKit's tab row doesn't
        // fit the window, the real offset differs and the book shifts a few pixels on the
        // first switch to the Inventory tab; a known gap, not yet worth tracking the real value.
        taller = 0;
    }

    /** The book {@code screen} has borrowed and shows now, or null. */
    public static @Nullable RecipeBookComponent<?> shownOn(Screen screen) {
        return borrower == screen && book != null && book.isVisible() ? book : null;
    }

    /**
     * The window's {@code width} as {@code screen}'s own code reads it ({@code BorrowedBookWidthMixin}):
     * with a borrowed book, wider by twice the frame's move, so vanilla's {@code (width - imageWidth) / 2}
     * is the moved {@code leftPos}, rounding included (the move is added whole). As is, without one.
     */
    public static int width(AbstractContainerScreen<?> screen, int width) {
        if (shownOn(screen) == null) return width;
        ScreenAccessor frame = (ScreenAccessor) screen;
        return width + 2 * (frame.openanywhere$leftPos() - (width - frame.openanywhere$imageWidth()) / 2);
    }

    /**
     * A recipe clicked on {@code clicked}: on a borrowed book, its own tab comes to the front
     * and the recipe waits for its screen. False when it's the book's own screen, which places it itself.
     */
    public static boolean placeAtHome(RecipeBookComponent<?> clicked, RecipeDisplayId recipe, boolean max) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || clicked != shownOn(screen)) return false;
        pending = new Pending(type, recipe, max);
        Tabs.pick(home);
        return true;
    }

    /** The server's row came: a recipe waiting on a tab it didn't put in front was refused. */
    static void answered(TabId shown) {
        if (!shown.equals(home)) pending = null;
    }

    /** Back in the world: the next screens start from vanilla's own book. */
    static void clear() {
        open = null;
        book = null;
        borrower = null;
        pending = null;
    }
}
