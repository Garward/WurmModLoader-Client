package com.garward.wurmmodloader.client.serverinfo;

import com.garward.wurmmodloader.client.api.serverinfo.ServerInfoRegistry;
import com.garward.wurmmodloader.client.modloader.ProxyClientHook;
import com.garward.wurmmodloader.client.modcomm.IChannelListener;
import com.garward.wurmmodloader.client.modcomm.ModComm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Client counterpart to {@code com.garward.wurmmodloader.core.serverinfo.ServerInfoChannel}.
 * Decodes the {@code wml.serverinfo} packet, updates {@link ServerInfoRegistry},
 * then fires {@code ServerInfoAvailableEvent}.
 */
public final class ServerInfoClientChannel {

    private static final Logger logger = Logger.getLogger(ServerInfoClientChannel.class.getName());
    private static final String CHANNEL_NAME = "wml.serverinfo";
    private static final byte PACKET_SERVER_INFO = 1;

    private static boolean initialized = false;

    private ServerInfoClientChannel() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            ModComm.registerChannel(CHANNEL_NAME, new IChannelListener() {
                @Override
                public void handleMessage(ByteBuffer message) {
                    handlePacket(message);
                }
            });
            logger.info("[ServerInfo] Registered " + CHANNEL_NAME + " client channel");
        } catch (Throwable t) {
            logger.warning("[ServerInfo] Failed to register " + CHANNEL_NAME + " client channel: " + t.getMessage());
        }
    }

    private static void handlePacket(ByteBuffer buffer) {
        try {
            byte packetType = buffer.get();
            if (packetType != PACKET_SERVER_INFO) {
                logger.warning("[ServerInfo] Unknown packet type: " + packetType);
                return;
            }
            String httpUri = readString(buffer);
            String modloaderVersion = readString(buffer);

            ServerInfoRegistry.update(httpUri, modloaderVersion);
            logger.info("[ServerInfo] Received server info: httpUri=" + httpUri + ", version=" + modloaderVersion);

            ProxyClientHook.fireServerInfoAvailableEvent(httpUri, modloaderVersion);
        } catch (Exception e) {
            logger.warning("[ServerInfo] Failed to parse packet: " + e.getMessage());
        }
    }

    private static String readString(ByteBuffer buffer) {
        short length = buffer.getShort();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
