package com.garward.wurmmodloader.client.modloader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads {@code mods/enabled.json} — a permissive master toggle file.
 *
 * <p>Schema (flat map of mod name → boolean):</p>
 * <pre>{@code
 * {
 *   "livemap": true,
 *   "wurmesp": false
 * }
 * }</pre>
 *
 * <p>Missing file, missing entries, or non-boolean values all resolve to <b>enabled</b>.
 * Only an explicit {@code false} disables a mod.</p>
 */
public final class EnabledRegistry {

    private static final Logger logger = Logger.getLogger(EnabledRegistry.class.getName());
    private static final EnabledRegistry EMPTY = new EnabledRegistry(Collections.emptyMap());

    private final Map<String, Boolean> overrides;

    private EnabledRegistry(Map<String, Boolean> overrides) {
        this.overrides = overrides;
    }

    public static EnabledRegistry load(File modsDir) {
        File file = new File(modsDir, "enabled.json");
        if (!file.exists()) {
            return EMPTY;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                logger.warning("[EnabledRegistry] enabled.json must be a JSON object {name:boolean}; ignoring.");
                return EMPTY;
            }
            Map<String, Boolean> map = new HashMap<>();
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                    map.put(entry.getKey(), value.getAsBoolean());
                } else {
                    logger.warning("[EnabledRegistry] non-boolean value for '"
                            + entry.getKey() + "' in enabled.json; treating as enabled.");
                }
            }
            logger.info("[EnabledRegistry] loaded " + map.size() + " entries from " + file);
            return new EnabledRegistry(map);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[EnabledRegistry] failed to read " + file + "; ignoring.", e);
            return EMPTY;
        }
    }

    public boolean isEnabled(String modName) {
        Boolean v = overrides.get(modName);
        return v == null || v;
    }

    public boolean isExplicitlyDisabled(String modName) {
        Boolean v = overrides.get(modName);
        return v != null && !v;
    }
}
