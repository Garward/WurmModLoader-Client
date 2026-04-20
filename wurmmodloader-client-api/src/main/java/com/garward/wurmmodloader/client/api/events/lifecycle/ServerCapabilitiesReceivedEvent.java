package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired when the client receives server mod capability information from the server.
 *
 * <p>This event is fired after the client connects to a server and receives the
 * list of active server-side mods. Client mods should subscribe to this event
 * to enable/disable features based on server capabilities.</p>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * @SubscribeEvent
 * public void onServerCapabilities(ServerCapabilitiesReceivedEvent event) {
 *     if (ServerCapabilities.hasServerMod("sprint_system")) {
 *         enableSprintFeatures();
 *         logger.info("Sprint system enabled (server version: " +
 *                    ServerCapabilities.getModVersion("sprint_system") + ")");
 *     } else {
 *         logger.info("Server does not have sprint_system, features disabled");
 *     }
 *
 *     // Check version compatibility
 *     if (ServerCapabilities.hasServerModWithVersion("power_scaling", "2.0")) {
 *         enablePowerScaling();
 *     }
 * }
 * }</pre>
 *
 * <h2>When NOT to Use This Event:</h2>
 * <ul>
 *   <li>For vanilla Wurm features - use {@link ClientInitEvent} instead</li>
 *   <li>For client-only mods - no server synchronization needed</li>
 * </ul>
 *
 * @since 0.2.0
 * @see com.garward.wurmmodloader.client.api.capabilities.ServerCapabilities
 */
public class ServerCapabilitiesReceivedEvent extends Event {

    private final List<ServerModInfo> serverMods;

    /**
     * Create a new ServerCapabilitiesReceivedEvent.
     *
     * @param serverMods List of server mod capabilities
     */
    public ServerCapabilitiesReceivedEvent(List<ServerModInfo> serverMods) {
        super(false); // Not cancellable
        this.serverMods = new ArrayList<>(serverMods);
    }

    /**
     * Get all server mod capabilities.
     *
     * @return Unmodifiable list of server mods
     */
    public List<ServerModInfo> getServerMods() {
        return new ArrayList<>(serverMods);
    }

    /**
     * Check if a specific mod is present on the server.
     *
     * @param modId Mod identifier to check
     * @return true if server has this mod
     */
    public boolean hasServerMod(String modId) {
        for (ServerModInfo mod : serverMods) {
            if (mod.getModId().equals(modId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get version of a server mod.
     *
     * @param modId Mod identifier
     * @return Version string, or null if mod not found
     */
    public String getModVersion(String modId) {
        for (ServerModInfo mod : serverMods) {
            if (mod.getModId().equals(modId)) {
                return mod.getVersion();
            }
        }
        return null;
    }

    /**
     * Information about a server-side mod.
     */
    public static class ServerModInfo {
        private final String modId;
        private final String version;
        private final String description;

        public ServerModInfo(String modId, String version, String description) {
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

    @Override
    public String toString() {
        return "ServerCapabilitiesReceivedEvent{serverMods=" + serverMods.size() + "}";
    }
}
