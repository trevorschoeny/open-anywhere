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
 * What a server lets its players open, one switch per kind, all on by default (Trev,
 * 2026-10-22): {@code config/openanywhere/server.json}, read when the server starts. A
 * server owner who sees the ender chest anywhere as a paid perk turns it off there. The
 * file is written with the defaults the first time, so there is something to edit.
 */
final class ServerSettings {

    private ServerSettings() {}

    private static final Logger LOG = LoggerFactory.getLogger(OpenAnywhere.MOD_ID);

    /** The file's key for each kind. */
    private static final Map<Openable, String> KEYS = new EnumMap<>(Map.of(
            Openable.SHULKER, "shulker_boxes", Openable.BUNDLE, "bundles", Openable.ENDER_CHEST, "ender_chest"));

    private static final Map<Openable, Boolean> ALLOWED = new EnumMap<>(Openable.class);

    /** Whether players on this server may open {@code kind}. */
    static boolean allows(Openable kind) {
        return ALLOWED.getOrDefault(kind, true);
    }

    /**
     * Reads the file, or writes it with every kind on. A missing or unreadable key keeps
     * its kind on: a broken file never takes away more than it says.
     */
    static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(OpenAnywhere.MOD_ID).resolve("server.json");
        ALLOWED.clear();
        try {
            if (Files.exists(path)) {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                KEYS.forEach((kind, key) -> {
                    if (root.has(key) && root.get(key).isJsonPrimitive()) ALLOWED.put(kind, root.get(key).getAsBoolean());
                });
                return;
            }
            JsonObject root = new JsonObject();
            KEYS.values().forEach(key -> root.addProperty(key, true));
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException | RuntimeException ex) {
            LOG.error("[openanywhere] couldn't read {}; everything stays allowed", path, ex);
            ALLOWED.clear();
        }
    }
}
