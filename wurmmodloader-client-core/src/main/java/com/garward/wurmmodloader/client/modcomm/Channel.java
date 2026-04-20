package com.garward.wurmmodloader.client.modcomm;

import com.wurmonline.communication.SocketConnection;

import java.nio.ByteBuffer;

/**
 * Channel object, created by {@link ModComm#registerChannel}.
 * Client-side counterpart to the server's Channel: the client has a single
 * server connection, so methods don't take a Player.
 */
public class Channel {
    int id;
    final IChannelListener listener;
    final String name;

    Channel(String name, IChannelListener listener) {
        this.id = -1;
        this.name = name;
        this.listener = listener;
    }

    /**
     * Send a message to the server on this channel. Channel must be active.
     */
    public void sendMessage(ByteBuffer message) {
        if (!isActive()) {
            throw new RuntimeException(String.format("Channel %s is not active", name));
        }
        try {
            SocketConnection conn = ModComm.getServerConnection();
            ByteBuffer buff = conn.getBuffer();
            buff.put(ModCommConstants.CMD_MODCOMM);
            buff.put(ModCommConstants.PACKET_MESSAGE);
            buff.putInt(id);
            buff.put(message);
            conn.flush();
        } catch (Exception e) {
            ModComm.logException(String.format("Error sending packet on channel %s", name), e);
        }
    }

    /** Check if this channel is active for the current server connection. */
    public boolean isActive() {
        return id > 0;
    }
}
