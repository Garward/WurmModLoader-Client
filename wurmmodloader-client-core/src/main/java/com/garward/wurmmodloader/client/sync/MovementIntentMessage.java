package com.garward.wurmmodloader.client.sync;

import java.nio.ByteBuffer;

/**
 * Client → Server message containing player movement intent.
 *
 * <p>Sent when the player presses/releases movement keys (WASD), allowing
 * the server to track and validate client-side prediction.
 *
 * @since 0.2.0
 */
public class MovementIntentMessage {
    private final long seqId;
    private final byte inputState;

    // Input state bit flags
    public static final byte FLAG_MOVE_FORWARD = 1 << 0;   // W
    public static final byte FLAG_MOVE_BACKWARD = 1 << 1;  // S
    public static final byte FLAG_MOVE_LEFT = 1 << 2;      // A
    public static final byte FLAG_MOVE_RIGHT = 1 << 3;     // D
    public static final byte FLAG_SPRINT = 1 << 4;         // Shift
    public static final byte FLAG_SNEAK = 1 << 5;          // Ctrl

    public MovementIntentMessage(long seqId, byte inputState) {
        this.seqId = seqId;
        this.inputState = inputState;
    }

    public long getSeqId() {
        return seqId;
    }

    public byte getInputState() {
        return inputState;
    }

    public boolean isMoveForward() {
        return (inputState & FLAG_MOVE_FORWARD) != 0;
    }

    public boolean isMoveBackward() {
        return (inputState & FLAG_MOVE_BACKWARD) != 0;
    }

    public boolean isMoveLeft() {
        return (inputState & FLAG_MOVE_LEFT) != 0;
    }

    public boolean isMoveRight() {
        return (inputState & FLAG_MOVE_RIGHT) != 0;
    }

    public boolean isSprint() {
        return (inputState & FLAG_SPRINT) != 0;
    }

    public boolean isSneak() {
        return (inputState & FLAG_SNEAK) != 0;
    }

    /**
     * Serialize to ByteBuffer for network transmission.
     */
    public void writeTo(ByteBuffer buffer) {
        buffer.put(WMLSyncMessageType.MOVEMENT_INTENT.getId());
        buffer.putLong(seqId);
        buffer.put(inputState);
    }

    /**
     * Deserialize from ByteBuffer (assumes message type byte already read).
     */
    public static MovementIntentMessage readFrom(ByteBuffer buffer) {
        long seqId = buffer.getLong();
        byte inputState = buffer.get();
        return new MovementIntentMessage(seqId, inputState);
    }

    @Override
    public String toString() {
        return String.format("MovementIntent[seq=%d, forward=%b, back=%b, left=%b, right=%b, sprint=%b]",
                seqId, isMoveForward(), isMoveBackward(), isMoveLeft(), isMoveRight(), isSprint());
    }
}
