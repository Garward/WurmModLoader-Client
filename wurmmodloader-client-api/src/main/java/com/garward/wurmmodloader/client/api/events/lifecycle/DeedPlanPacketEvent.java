package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

import java.nio.ByteBuffer;

/**
 * Fired when the client receives a deed-plan (token/village bounds) packet
 * from the server — specifically {@code SimpleServerConnectionClass
 * .reallyHandleCmdShowDeedPlan(ByteBuffer)}.
 *
 * <p>The buffer is a {@link ByteBuffer#duplicate()} positioned at the packet
 * payload; handlers may read freely without disturbing the engine's parse.
 * Cancellable — cancelling suppresses vanilla deed-plan rendering.
 *
 * @since 0.3.0
 */
public class DeedPlanPacketEvent extends Event {

    private final ByteBuffer buffer;

    public DeedPlanPacketEvent(ByteBuffer buffer) {
        super(true);
        this.buffer = buffer;
    }

    /** Duplicate of the packet buffer — safe to read without affecting the engine. */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public String toString() {
        return "DeedPlanPacket[remaining=" + (buffer == null ? 0 : buffer.remaining()) + "]";
    }
}
