package com.garward.wurmmodloader.client.api.events.client.movement;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired on the client whenever the game applies a new authoritative position
 * for the local player that originated from the server.
 * <p>
 * Use this as the primary reconciliation hook for client-side prediction.
 */
public final class AuthoritativePlayerPositionEvent extends Event {

    private final float x;
    private final float y;
    private final float height;
    private final long sequence;

    public AuthoritativePlayerPositionEvent(float x, float y, float height, long sequence) {
        super(false);
        this.x = x;
        this.y = y;
        this.height = height;
        this.sequence = sequence;
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

    /**
     * Optional sequence / tick identifier to correlate with client-side prediction buffers.
     */
    public long getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return "AuthoritativePlayerPositionEvent{" +
            "x=" + x +
            ", y=" + y +
            ", height=" + height +
            ", sequence=" + sequence +
            '}';
    }
}
