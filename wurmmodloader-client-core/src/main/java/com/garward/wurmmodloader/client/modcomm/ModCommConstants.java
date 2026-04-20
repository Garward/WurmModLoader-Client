package com.garward.wurmmodloader.client.modcomm;

/**
 * Shared wire-protocol constants for the client-side ModComm port.
 * Must stay byte-for-byte identical to the server-side
 * {@code com.garward.wurmmodloader.modcomm.ModCommConstants}.
 */
public class ModCommConstants {
    public static final byte CMD_MODCOMM = -100;
    public static final String MARKER = "[ModCommV1]";
    public static final byte PROTO_VERSION = 1;

    public static final byte PACKET_MESSAGE = 1;
    public static final byte PACKET_CHANNELS = 2;
}
