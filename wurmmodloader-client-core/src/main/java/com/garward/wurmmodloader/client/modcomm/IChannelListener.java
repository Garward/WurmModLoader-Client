package com.garward.wurmmodloader.client.modcomm;

import java.nio.ByteBuffer;

/**
 * Listener for mod channels. Implement and register with {@link ModComm#registerChannel}.
 * Client-side counterpart to the server's {@code com.garward.wurmmodloader.modcomm.IChannelListener}.
 */
public interface IChannelListener {
    /** Handle a message from the server. */
    default void handleMessage(ByteBuffer message) {
    }

    /** Called when the server activates this channel for the current connection. */
    default void onServerConnected() {
    }
}
