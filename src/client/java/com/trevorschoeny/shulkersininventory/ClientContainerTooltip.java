package com.trevorschoeny.shulkersininventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a shulker box's or the ender chest's 27 stacks as a 9 by 3 grid in its tooltip,
 * with the selected stack highlighted. It reuses vanilla's bundle tooltip sprites and
 * cell size, so the three items look like one family.
 *
 * <p>ponytail: no floating name over the selected stack yet (vanilla's bundle has one);
 * add it once the grid has been seen in game.
 */
final class ClientContainerTooltip implements ClientTooltipComponent {

    private static final Identifier SLOT_BACKGROUND = Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/bundle/slot_highlight_front");

    /** Vanilla's bundle cell: 24 pixels, the item inset 4. */
    private static final int CELL = 24;
    private static final int ITEM_INSET = 4;
    private static final int COLUMNS = 9;
    /** Space under the grid, so the tooltip's frame doesn't sit on the last row. */
    private static final int BOTTOM_MARGIN = 4;

    private final ContainerTooltip grid;

    ClientContainerTooltip(ContainerTooltip grid) {
        this.grid = grid;
    }

    private int rows() {
        return (grid.items().size() + COLUMNS - 1) / COLUMNS;
    }

    @Override
    public int getHeight(Font font) {
        return rows() * CELL + BOTTOM_MARGIN;
    }

    @Override
    public int getWidth(Font font) {
        return COLUMNS * CELL;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int selected = selectedIndex();
        for (int i = 0; i < grid.items().size(); i++) {
            int cellX = x + (i % COLUMNS) * CELL;
            int cellY = y + (i / COLUMNS) * CELL;
            ItemStack stack = grid.items().get(i);
            boolean isSelected = i == selected;

            if (isSelected) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, cellX, cellY, CELL, CELL);
            else graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND, cellX, cellY, CELL, CELL);
            if (!stack.isEmpty()) {
                graphics.item(stack, cellX + ITEM_INSET, cellY + ITEM_INSET, i);
                graphics.itemDecorations(font, stack, cellX + ITEM_INSET, cellY + ITEM_INSET);
            }
            if (isSelected) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, cellX, cellY, CELL, CELL);
        }
    }

    /**
     * The player's selection, or -1. A selection only exists while one item is hovered
     * (moving off clears it), and that hovered item is the only grid tooltip on screen,
     * so the selection is this grid's.
     */
    private static int selectedIndex() {
        var player = Minecraft.getInstance().player;
        Selection.Entry entry = player == null ? null : Selection.get(player);
        return entry == null ? -1 : entry.index();
    }
}
