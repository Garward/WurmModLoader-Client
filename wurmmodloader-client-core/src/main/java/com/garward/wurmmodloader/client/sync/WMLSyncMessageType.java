package com.garward.wurmmodloader.client.sync;

/**
 * Message types for the WML_SYNC ModComm channel.
 *
 * <p>This channel enables client-server synchronization for prediction systems,
 * allowing the client to send movement intent and receive corrections from the server.
 *
 * @since 0.2.0
 */
public enum WMLSyncMessageType {
    /**
     * Client → Server: Player movement intent (WASD input).
     * Contains: seqId (long), inputState (byte flags)
     */
    MOVEMENT_INTENT((byte) 1),

    /**
     * Client → Server: Optional predicted position for debugging.
     * Contains: seqId (long), x (float), y (float), height (float)
     */
    PREDICTION_STATE((byte) 2),

    /**
     * Server → Client: Position correction when prediction deviates.
     * Contains: seqId (long), x (float), y (float), height (float), reason (byte)
     */
    SERVER_CORRECTION((byte) 3);

    private final byte id;

    WMLSyncMessageType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static WMLSyncMessageType fromId(byte id) {
        for (WMLSyncMessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown WMLSync message type: " + id);
    }
}
