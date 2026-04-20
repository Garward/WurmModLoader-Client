package com.garward.wurmmodloader.client.api.capabilities;

import com.garward.wurmmodloader.client.api.events.lifecycle.ServerCapabilitiesReceivedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Client-side API for querying server mod capabilities.
 *
 * <p>This class provides a simple API for client mods to check which server-side
 * mods are active before enabling client features. Capabilities are received
 * from the server via ModComm when the player connects.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Check if server has a mod
 * if (ServerCapabilities.hasServerMod("sprint_system")) {
 *     enableSprintFeatures();
 * }
 *
 * // Check mod version
 * if (ServerCapabilities.hasServerModWithVersion("power_scaling", "2.0")) {
 *     enablePowerScaling();
 * }
 *
 * // Get exact version
 * String version = ServerCapabilities.getModVersion("sprint_system");
 * logger.info("Server sprint_system version: " + version);
 *
 * // List all server mods
 * for (ModInfo mod : ServerCapabilities.getServerMods()) {
 *     logger.info("Server mod: " + mod);
 * }
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * <p>This class is thread-safe. All methods are synchronized.</p>
 *
 * @since 0.2.0
 * @see ServerCapabilitiesReceivedEvent
 */
public class ServerCapabilities {

    private static final Logger logger = Logger.getLogger(ServerCapabilities.class.getName());

    // Map of modId -> ModInfo
    private static final Map<String, ModInfo> serverMods = new HashMap<>();

    // Flag to track if we've received capabilities
    private static boolean capabilitiesReceived = false;

    /**
     * Update server capabilities (called when ModComm receives capability packet).
     *
     * @param mods List of server mod information
     */
    public static synchronized void updateCapabilities(List<ModInfo> mods) {
        serverMods.clear();

        for (ModInfo mod : mods) {
            serverMods.put(mod.getModId(), mod);
        }

        capabilitiesReceived = true;

        logger.info("[ServerCapabilities] Received " + mods.size() + " server mod capabilities");
        for (ModInfo mod : mods) {
            logger.info("[ServerCapabilities]   " + mod);
        }
    }

    /**
     * Check if server capabilities have been received.
     *
     * <p>Returns false if the client hasn't connected to a server yet,
     * or if the server doesn't have WurmModLoader.</p>
     *
     * @return true if capabilities have been received from server
     */
    public static synchronized boolean hasReceivedCapabilities() {
        return capabilitiesReceived;
    }

    /**
     * Check if a specific server mod is active.
     *
     * @param modId Mod identifier (e.g., "sprint_system")
     * @return true if server has this mod active
     */
    public static synchronized boolean hasServerMod(String modId) {
        if (!capabilitiesReceived) {
            logger.warning("[ServerCapabilities] Checking for '" + modId +
                          "' but capabilities not received yet - assuming false");
            return false;
        }
        return serverMods.containsKey(modId);
    }

    /**
     * Check if a specific server mod is active with at least a minimum version.
     *
     * <p>Uses simple string comparison: {@code serverVersion.compareTo(minVersion) >= 0}.
     * For proper semantic versioning, use a dedicated library.</p>
     *
     * @param modId Mod identifier
     * @param minVersion Minimum required version
     * @return true if server has this mod with version >= minVersion
     */
    public static synchronized boolean hasServerModWithVersion(String modId, String minVersion) {
        ModInfo mod = serverMods.get(modId);
        if (mod == null) {
            return false;
        }
        return mod.getVersion().compareTo(minVersion) >= 0;
    }

    /**
     * Get the version of a server mod.
     *
     * @param modId Mod identifier
     * @return Version string, or null if mod not found
     */
    public static synchronized String getModVersion(String modId) {
        ModInfo mod = serverMods.get(modId);
        return (mod != null) ? mod.getVersion() : null;
    }

    /**
     * Get information about a server mod.
     *
     * @param modId Mod identifier
     * @return ModInfo object, or null if mod not found
     */
    public static synchronized ModInfo getModInfo(String modId) {
        return serverMods.get(modId);
    }

    /**
     * Get all server mods.
     *
     * @return List of all server mods (unmodifiable copy)
     */
    public static synchronized List<ModInfo> getServerMods() {
        return new ArrayList<>(serverMods.values());
    }

    /**
     * Clear all server capabilities.
     *
     * <p>Called when disconnecting from server or connecting to a new server.</p>
     */
    public static synchronized void clearCapabilities() {
        serverMods.clear();
        capabilitiesReceived = false;
        logger.info("[ServerCapabilities] Cleared server capabilities");
    }

    /**
     * Information about a server-side mod.
     */
    public static class ModInfo {
        private final String modId;
        private final String version;
        private final String description;

        public ModInfo(String modId, String version, String description) {
            this.modId = modId;
            this.version = version;
            this.description = description;
        }

        public String getModId() {
            return modId;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return modId + ":" + version +
                   (description.isEmpty() ? "" : " (" + description + ")");
        }
    }
}
