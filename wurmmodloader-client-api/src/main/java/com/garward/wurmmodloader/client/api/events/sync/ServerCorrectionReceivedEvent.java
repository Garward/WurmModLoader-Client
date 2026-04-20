package com.garward.wurmmodloader.client.api.events.sync;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired on the client when the server sends a position correction via WML_SYNC channel.
 *
 * <p>This event allows client-side prediction mods to reconcile predicted positions
 * with authoritative server corrections.
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onServerCorrection(ServerCorrectionReceivedEvent event) {
 *     long seqId = event.getSeqId();
 *     float serverX = event.getX();
 *     float serverY = event.getY();
 *     // Snap or lerp to correct position
 *     if (event.getReason() == CorrectionReason.POSITION_DEVIATION) {
 *         // Soft correction - lerp smoothly
 *     } else {
 *         // Hard correction - snap immediately
 *     }
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class ServerCorrectionReceivedEvent extends Event {
    private final long seqId;
    private final float x;
    private final float y;
    private final float height;
    private final CorrectionReason reason;

    /**
     * Reasons for server corrections (mirror of ServerCorrectionMessage.CorrectionReason).
     */
    public enum CorrectionReason {
        POSITION_DEVIATION((byte) 0),
        COLLISION((byte) 1),
        STAMINA_EXHAUSTED((byte) 2),
        INVALID_TERRAIN((byte) 3),
        SPEED_LIMIT((byte) 4),
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

    public ServerCorrectionReceivedEvent(long seqId, float x, float y, float height, CorrectionReason reason) {
        super(false); // Not cancellable
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

    @Override
    public String toString() {
        return String.format("ServerCorrectionReceived[seq=%d, pos=(%.2f, %.2f, %.2f), reason=%s]",
                seqId, x, y, height, reason);
    }
}
