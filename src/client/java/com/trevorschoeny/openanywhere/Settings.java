package com.trevorschoeny.openanywhere;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's settings (Trev, 2026-10-22), the one place they live:
 * {@code config/openanywhere/settings.json}, all on by default. Changed from the Open
 * Anywhere tab in Inventory Plus's settings menu when IP is installed; without it, the file
 * or the defaults apply. Every setter saves.
 *
 * <p>Client only, and a preference, not a permission: a kind turned off never sends an
 * open, and the server takes whatever the client asks for.
 */
public final class Settings {

    private Settings() {}

    private static final Logger LOG = LoggerFactory.getLogger(OpenAnywhere.MOD_ID);

    /** The file's key for each kind. */
    private static final Map<Openable, String> KEYS = new EnumMap<>(Map.of(
            Openable.SHULKER, "shulker_boxes", Openable.BUNDLE, "bundles", Openable.ENDER_CHEST, "ender_chest"));

    private static final Map<Openable, Boolean> OPENS = new EnumMap<>(Openable.class);
    private static boolean doubleClick = true;
    private static boolean wheel = true;

    /** Whether {@code kind} opens at all. Turning one off leaves any already open until closed. */
    public static boolean opens(Openable kind) { return OPENS.getOrDefault(kind, true); }
    /** Whether a double-click opens and closes; the Open key works either way. */
    public static boolean doubleClick() { return doubleClick; }
    /** Whether the wheel moves between tabs. */
    public static boolean wheel() { return wheel; }

    public static void setOpens(Openable kind, boolean on) { OPENS.put(kind, on); save(); }
    public static void setDoubleClick(boolean on) { doubleClick = on; save(); }
    public static void setWheel(boolean on) { wheel = on; save(); }

    /** Everything back to on, and saves: Reset to Defaults and Inventory Plus's Reset everything. */
    public static void reset() {
        OPENS.clear();
        doubleClick = true;
        wheel = true;
        save();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(OpenAnywhere.MOD_ID).resolve("settings.json");
    }

    /**
     * Reads the file once, at client init. A missing file, or a missing or unreadable key,
     * leaves that setting on. Nothing is written until a setting changes.
     */
    static void load() {
        Path path = path();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            KEYS.forEach((kind, key) -> {
                if (root.has(key)) OPENS.put(kind, root.get(key).getAsBoolean());
            });
            if (root.has("double_click")) doubleClick = root.get("double_click").getAsBoolean();
            if (root.has("wheel_switches_tabs")) wheel = root.get("wheel_switches_tabs").getAsBoolean();
        } catch (IOException | RuntimeException ex) {
            LOG.error("[openanywhere] couldn't read {}; using the defaults", path, ex);
        }
    }

    private static void save() {
        Path path = path();
        JsonObject root = new JsonObject();
        KEYS.forEach((kind, key) -> root.addProperty(key, opens(kind)));
        root.addProperty("double_click", doubleClick);
        root.addProperty("wheel_switches_tabs", wheel);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException ex) {
            LOG.error("[openanywhere] couldn't write {}; the change won't last past this session", path, ex);
        }
    }
}
