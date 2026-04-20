package com.garward.wurmmodloader.client.sync;

import java.nio.ByteBuffer;

/**
 * Server → Client message containing position correction.
 *
 * <p>Sent when the server detects that the client's prediction has deviated
 * beyond acceptable thresholds, or when game rules prevent the predicted movement.
 *
 * @since 0.2.0
 */
public class ServerCorrectionMessage {
    private final long seqId;
    private final float x;
    private final float y;
    private final float height;
    private final CorrectionReason reason;

    /**
     * Reasons for server corrections (for debugging/logging).
     */
    public enum CorrectionReason {
        POSITION_DEVIATION((byte) 0),    // Prediction drifted too far
        COLLISION((byte) 1),              // Hit obstacle/terrain
        STAMINA_EXHAUSTED((byte) 2),     // No stamina for movement
        INVALID_TERRAIN((byte) 3),       // Tried to move into water/void
        SPEED_LIMIT((byte) 4),           // Moving too fast (anti-cheat)
        OTHER((byte) 99);

        private final byte id;

        CorrectionReason(byte id) {
            this.id = id;
        }

        public byte getId() {
            return id;
        }

        public static CorrectionReason fromId(byte id) {
            for (CorrectionReason reason : values()) {
                if (reason.id == id) {
                    return reason;
                }
            }
            return OTHER;
        }
    }

    public ServerCorrectionMessage(long seqId, float x, float y, float height, CorrectionReason reason) {
        this.seqId = seqId;
        this.x = x;
        this.y = y;
        this.height = height;
        this.reason = reason;
    }

    public long getSeqId() {
        return seqId;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getHeight() {
        return height;
    }

    public CorrectionReason getReason() {
        return reason;
    }

    /**
     * Serialize to ByteBuffer for network transmission.
     */
    public void writeTo(ByteBuffer buffer) {
        buffer.put(WMLSyncMessageType.SERVER_CORRECTION.getId());
        buffer.putLong(seqId);
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(height);
        buffer.put(reason.getId());
    }

    /**
     * Deserialize from ByteBuffer (assumes message type byte already read).
     */
    public static ServerCorrectionMessage readFrom(ByteBuffer buffer) {
        long seqId = buffer.getLong();
        float x = buffer.getFloat();
        float y = buffer.getFloat();
        float height = buffer.getFloat();
        CorrectionReason reason = CorrectionReason.fromId(buffer.get());
        return new ServerCorrectionMessage(seqId, x, y, height, reason);
    }

    @Override
    public String toString() {
        return String.format("ServerCorrection[seq=%d, pos=(%.2f, %.2f, %.2f), reason=%s]",
                seqId, x, y, height, reason);
    }
}
