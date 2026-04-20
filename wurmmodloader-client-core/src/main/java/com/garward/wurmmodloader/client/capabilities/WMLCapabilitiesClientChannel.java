package com.garward.wurmmodloader.client.capabilities;

import com.garward.wurmmodloader.client.api.capabilities.ServerCapabilities;
import com.garward.wurmmodloader.client.api.events.lifecycle.ServerCapabilitiesReceivedEvent;
import com.garward.wurmmodloader.client.modloader.ProxyClientHook;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Client-side WML_CAPABILITIES ModComm channel for receiving server mod information.
 *
 * <p>This channel receives a list of active server-side mods when the client connects.
 * It stores this information in {@link ServerCapabilities} and fires
 * {@link ServerCapabilitiesReceivedEvent} for mods to react to.</p>
 *
 * <h2>Automatic Behavior:</h2>
 * <ul>
 *   <li>Channel initializes on client startup</li>
 *   <li>Server sends capabilities when player logs in</li>
 *   <li>Client receives capabilities and fires event</li>
 *   <li>Mods query {@link ServerCapabilities} to check server mods</li>
 * </ul>
 *
 * <h2>Client Mod Usage:</h2>
 * <pre>{@code
 * @SubscribeEvent
 * public void onServerCapabilities(ServerCapabilitiesReceivedEvent event) {
 *     if (ServerCapabilities.hasServerMod("sprint_system")) {
 *         enableSprintFeatures();
 *     }
 * }
 * }</pre>
 *
 * @since 0.2.0
 * @see ServerCapabilities
 * @see ServerCapabilitiesReceivedEvent
 */
public class WMLCapabilitiesClientChannel {

    private static final Logger logger = Logger.getLogger(WMLCapabilitiesClientChannel.class.getName());
    private static final String CHANNEL_NAME = "WML_CAPABILITIES";

    // Packet types
    private static final byte PACKET_SERVER_CAPABILITIES = 1;

    private static boolean initialized = false;

    /**
     * Initialize the WML_CAPABILITIES client channel. Called during client startup.
     */
    public static void initialize() {
        if (!initialized) {
            initialized = true;
            logger.info("[WMLCapabilities] WML_CAPABILITIES client channel initialized");
            // TODO: Actual ModComm client registration when we have full ModComm client support
        }
    }

    /**
     * Handle incoming capability packet from server.
     *
     * <p>This method is called by ModComm when the server sends capability information.
     * It parses the packet, updates {@link ServerCapabilities}, and fires an event.</p>
     *
     * @param buffer ByteBuffer containing the capability packet
     */
    public static void handleCapabilitiesPacket(ByteBuffer buffer) {
        try {
            // Read packet type
            byte packetType = buffer.get();

            if (packetType != PACKET_SERVER_CAPABILITIES) {
                logger.warning("[WMLCapabilities] Unknown packet type: " + packetType);
                return;
            }

            // Read number of mods
            short modCount = buffer.getShort();

            logger.info("[WMLCapabilities] Receiving " + modCount + " server mod capabilities");

            // Read each mod
            List<ServerCapabilities.ModInfo> mods = new ArrayList<>();
            for (int i = 0; i < modCount; i++) {
                String modId = readString(buffer);
                String version = readString(buffer);
                String description = readString(buffer);

                mods.add(new ServerCapabilities.ModInfo(modId, version, description));
            }

            // Update ServerCapabilities singleton
            ServerCapabilities.updateCapabilities(mods);

            // Fire event for mods to react
            fireServerCapabilitiesReceivedEvent(mods);

        } catch (Exception e) {
            logger.severe("[WMLCapabilities] Failed to parse capabilities packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Read a UTF-8 string from buffer with length prefix.
     *
     * @param buffer Source buffer
     * @return Decoded string
     */
    private static String readString(ByteBuffer buffer) {
        short length = buffer.getShort();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Fire ServerCapabilitiesReceivedEvent to notify mods.
     *
     * @param mods List of server mods
     */
    private static void fireServerCapabilitiesReceivedEvent(List<ServerCapabilities.ModInfo> mods) {
        // Convert ModInfo to event's ServerModInfo
        List<ServerCapabilitiesReceivedEvent.ServerModInfo> eventMods = new ArrayList<>();
        for (ServerCapabilities.ModInfo mod : mods) {
            eventMods.add(new ServerCapabilitiesReceivedEvent.ServerModInfo(
                mod.getModId(),
                mod.getVersion(),
                mod.getDescription()
            ));
        }

        ServerCapabilitiesReceivedEvent event = new ServerCapabilitiesReceivedEvent(eventMods);

        // Fire event via ProxyClientHook
        ProxyClientHook.getInstance().fireServerCapabilitiesReceived(event);

        logger.info("[WMLCapabilities] Fired ServerCapabilitiesReceivedEvent with " + mods.size() + " mods");
    }

    /**
     * Clear capabilities when disconnecting from server.
     */
    public static void clearCapabilities() {
        ServerCapabilities.clearCapabilities();
        logger.info("[WMLCapabilities] Cleared server capabilities (disconnected)");
    }
}
