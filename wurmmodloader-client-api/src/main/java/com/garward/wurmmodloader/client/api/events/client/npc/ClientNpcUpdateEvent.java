package com.garward.wurmmodloader.client.api.events.client.npc;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the client advances an NPC / creature renderable's game tick.
 * <p>
 * This gives mods a clean hook for interpolation / dead-reckoning logic
 * on the client, without touching any rendering code directly.
 */
public final class ClientNpcUpdateEvent extends Event {

    private final long creatureId;
    private final float x;
    private final float y;
    private final float height;
    private final float deltaSeconds;

    public ClientNpcUpdateEvent(long creatureId, float x, float y, float height, float deltaSeconds) {
        super(false);
        this.creatureId = creatureId;
        this.x = x;
        this.y = y;
        this.height = height;
        this.deltaSeconds = deltaSeconds;
    }

    public long getCreatureId() {
        return creatureId;
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

    public float getDeltaSeconds() {
        return deltaSeconds;
    }

    @Override
    public String toString() {
        return "ClientNpcUpdateEvent{" +
            "creatureId=" + creatureId +
            ", x=" + x +
            ", y=" + y +
            ", height=" + height +
            ", deltaSeconds=" + deltaSeconds +
            '}';
    }
}
