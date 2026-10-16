package com.trevorschoeny.shulkersininventory;

/**
 * The tooltip view's switches, one per item (design: six toggles; the three for the
 * container view arrive with it). Plain fields until ConfigKit lands, the same stopgap
 * every suite mod uses; then they become ConfigKit declarations and only this class
 * changes. Off means vanilla's own tooltip for that item.
 */
public final class ClientSettings {

    private ClientSettings() {}

    private static volatile boolean shulkerTooltip = true;
    private static volatile boolean enderTooltip = true;
    private static volatile boolean bundleTooltip = true;

    public static boolean shulkerTooltip() { return shulkerTooltip; }
    public static boolean enderTooltip()   { return enderTooltip; }
    public static boolean bundleTooltip()  { return bundleTooltip; }
}
