package com.trevorschoeny.openanywhere;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevorschoeny.openanywhere.mixin.ScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client init: the Option-click that opens an item (and closes it again), the sections
 * ({@link ClientPanels}), and the mirror of what the server opened ({@link SessionS2C}).
 * Escape is vanilla's: it closes the screen, which closes everything on it.
 */
public final class OpenAnywhereClient implements ClientModInitializer {

    static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OpenAnywhere.MOD_ID, "keys"));

    /**
     * An optional key: held while clicking an item, it opens it in one click. Unbound by
     * default; double-click is the way in (Trev, 2026-10-22), and this is for players who
     * want a key instead, set to anything in Controls.
     */
    public static final KeyMapping OPEN = new KeyMapping(
            "key.openanywhere.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** Sessions just opened whose tab isn't showing yet, by section id. */
    private static final java.util.Map<String, Session> AWAITING = new java.util.HashMap<>();

    /** True while {@code slot} holds an item just opened whose tab hasn't shown yet: no grey for it. */
    static boolean awaitingTab(Slot slot) {
        if (AWAITING.isEmpty()) return false;
        var screen = Minecraft.getInstance().gui.screen();
        AWAITING.keySet().removeIf(id -> screen == null || ContainerRegion.anchor(id).box(screen) != null);
        for (Session s : AWAITING.values()) if (s.container() == slot.container && s.containerSlot() == slot.getContainerSlot()) return true;
        return false;
    }

    /**
     * The double-click window (Trev, 2026-10-22): half a second, as long as the cursor
     * stays put. Through it the lifted item is drawn where it sat ({@link #heldAt}), so a
     * double-click shows no movement; moving the cursor ends it, and the item goes to the
     * cursor as any picked-up item does.
     */
    private static final long HOLD_MS = 500;
    // The first click's slot on screen and where the mouse was.
    private static int heldX, heldY;
    private static double firstMouseX, firstMouseY;

    /**
     * Where the item on the cursor is drawn: its slot's place while a double-click may still
     * come (just picked up, the window open, the cursor still), else {@code null}, the cursor.
     */
    public static int @org.jspecify.annotations.Nullable [] heldAt(int mouseX, int mouseY) {
        if (lastSlot < 0) return null;
        // The screen draws at whole pixels; the click came in at fractions of one.
        if (net.minecraft.util.Util.getMillis() - lastClick >= HOLD_MS
                || mouseX != (int) Math.floor(firstMouseX) || mouseY != (int) Math.floor(firstMouseY)) {
            lastSlot = -1;
            return null;
        }
        return new int[] {heldX, heldY};
    }
    // The last plain left click on a slot: which slot, and when. -1: none to pair with.
    private static int lastSlot = -1;
    private static long lastClick;

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN);
        ClientPlayNetworking.registerGlobalReceiver(SessionS2C.TYPE, (payload, context) -> mirror(context.player(), payload));
        ClientPanels.register();
                // The bundle's host is vanilla's chest menu, so it gets vanilla's chest screen.
        MenuScreens.register(ContainerHost.TYPE, ContainerScreen::new);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            // The trigger is the real mouse, read before the screen sees the click, so a
            // simulated click (a sort mod, Mouse Tweaks) never opens anything. On an item
            // that's already open it closes it (the server decides which).
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!ClientPlayNetworking.canSend(OpenC2S.TYPE)) return true; // server without the mod
                Slot hovered = ((ScreenAccessor) containerScreen).openanywhere$hoveredSlot();
                if (hovered == null) return true;
                ItemStack carried = containerScreen.getMenu().getCarried();
                if (isOpenClick(event)) {
                    if (Openable.of(hovered.getItem()) == null || !carried.isEmpty()) return true;
                    // On creative's screen, only its inventory tab, by the real menu's index.
                    if (s instanceof CreativeModeInventoryScreen c && !c.isInventoryOpen()) return true;
                    ClientPlayNetworking.send(new OpenC2S(s instanceof CreativeModeInventoryScreen ? realIndex(client.player, hovered) : hovered.index, false));
                    return false; // instead of the click
                }
                return !doubleClick(s, event, hovered, carried, client.player);
            });
        });
    }

    /**
     * A plain left double-click on an openable item opens it (Trev, 2026-10-22); on an
     * item that's open, it closes it. Shift+double-click stays vanilla's "move all".
     *
     * <p>The first click is vanilla's: it lifts the item onto the cursor (an open item's
     * slot is locked, so it stays put). The second, on the same slot within vanilla's
     * double-click window, would be vanilla's "gather all of this item"; it is taken
     * instead and sent as an open, the server putting the lifted item back first. A stack
     * of boxes (Carpet) never opens, so its double-click stays vanilla's gather; a stack of
     * ender chests opens the ender chest (Trev: gather is given up for ender chests).
     *
     * @return true when the click was taken
     */
    private static boolean doubleClick(Screen screen, MouseButtonEvent event, Slot hovered, ItemStack carried, Player player) {
        // Creative's inventory tab shows the player's slots on a menu of its own; the server
        // has the player's real inventory menu open, so the slot is named by its index there.
        // Creative's other tabs aren't the inventory: nothing opens there.
        boolean creative = screen instanceof CreativeModeInventoryScreen;
        if (creative && !((CreativeModeInventoryScreen) screen).isInventoryOpen()) return false;
        int target = creative ? realIndex(player, hovered) : hovered.index;
        if (target < 0) return false;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.hasShiftDown()) {
            lastSlot = -1;
            return false;
        }
        long now = net.minecraft.util.Util.getMillis();
        boolean second = target == lastSlot && now - lastClick < HOLD_MS
                && event.x() == firstMouseX && event.y() == firstMouseY;
        if (!second) {
            // A first click worth pairing: on an openable item, or an open one.
            lastSlot = Openable.of(hovered.getItem()) != null && carried.isEmpty() ? target : -1;
            lastClick = now;
            firstMouseX = event.x();
            firstMouseY = event.y();
            ScreenAccessor edge = (ScreenAccessor) screen;
            heldX = edge.openanywhere$leftPos() + hovered.x;
            heldY = edge.openanywhere$topPos() + hovered.y;
            return false;
        }
        lastSlot = -1; // a third click starts over
        if (!hovered.hasItem() && openable(carried)) {
            if (creative) {
                // Creative keeps the cursor on the client and tells the server each slot's
                // contents itself, so the item goes back here, the way creative's own click
                // puts it (a creative slot update), then opens as if never lifted: as if the
                // player had opened the container itself, so its screen replaces creative's.
                player.inventoryMenu.getSlot(target).set(carried);
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                ClientPlayNetworking.send(new OpenC2S(target, false));
            } else {
                // Back in its slot now, on this side, rather than once the server says so:
                // the lift lasts only as long as the player's own double-click. The server
                // puts it back too, and its sync corrects this if it refuses.
                hovered.set(carried);
                player.containerMenu.setCarried(ItemStack.EMPTY);
                ClientPlayNetworking.send(new OpenC2S(hovered.index, true));
            }
            return true;
        }
        if (carried.isEmpty() && isOpen(player, hovered)) {
            ClientPlayNetworking.send(new OpenC2S(hovered.index, false));
            return true;
        }
        return false;
    }

    /** Creative's slot {@code shown} on the player's real inventory menu: the slot holding the same place. -1 for none. */
    private static int realIndex(Player player, Slot shown) {
        for (Slot real : player.inventoryMenu.slots) {
            if (real.container == shown.container && real.getContainerSlot() == shown.getContainerSlot()) return real.index;
        }
        return -1;
    }

    /** True when the item in {@code slot} is open: a session holds that slot of the open menu. */
    private static boolean isOpen(Player player, Slot slot) {
        for (Session s : Sessions.of(player)) if (s.menu() == player.containerMenu && s.menuSlot() == slot.index) return true;
        return false;
    }

    /** An item a double-click opens: one box or bundle, or any stack of ender chests. */
    private static boolean openable(ItemStack stack) {
        Openable kind = Openable.of(stack);
        return kind == Openable.ENDER_CHEST || kind != null && stack.getCount() == 1;
    }

    /**
     * A left or right click with the open key held. A modifier key is read from the click
     * itself, as vanilla reads Shift, so a click arriving with Alt set counts whatever the
     * keyboard says now; any other key is read from the keyboard.
     */
    private static boolean isOpenClick(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN);
        if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return switch (key.getValue()) {
            case InputConstants.KEY_LALT, InputConstants.KEY_RALT -> event.hasAltDown();
            case InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> event.hasShiftDown();
            case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> event.hasControlDown();
            default -> InputConstants.isKeyDown(key.getValue());
        };
    }

    /**
     * Mirrors one server decision. The slot is resolved on the client's own copy of the
     * now-open menu, so the client's slot lock and grey name the same slot as the server's.
     */
    private static void mirror(Player player, SessionS2C payload) {
        Openable kind = payload.kind();
        if (!payload.open()) {
            Sessions.remove(player, kind, payload.lane());
            // Closed with its button or a double-click: back to the inventory screen.
            if (payload.toInventory()) Minecraft.getInstance().gui.setScreen(new InventoryScreen(player));
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        boolean onMenu = payload.menuSlot() >= 0 && payload.menuSlot() < menu.slots.size();
        Slot slot = onMenu ? menu.getSlot(payload.menuSlot()) : null;
        Session session = new Session(kind, payload.lane(), menu, slot == null ? null : slot.container,
                slot == null ? -1 : slot.getContainerSlot(), payload.menuSlot(),
                slot == null ? ItemStack.EMPTY : slot.getItem());
        Sessions.put(player, session);
        // What was just opened is what the player wants to see: its tab is picked. MenuKit
        // shows it a frame later; until then its slot isn't greyed, so that frame looks as
        // it did before the click instead of flashing the box greyed in the chest.
        if (session.stacked()) {
            String id = ClientPanels.sectionIdOf(session);
            Tabs.pick(id);
            AWAITING.put(id, session);
        }
    }
}
