package com.garward.wurmmodloader.client.api.events.client.movement;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired once per client tick immediately before the local player's transform
 * is updated by the vanilla client.
 * <p>
 * Recommended hook for injecting predicted transforms before rendering.
 */
public final class ClientPrePlayerUpdateEvent extends Event {

    private final float deltaSeconds;

    public ClientPrePlayerUpdateEvent(float deltaSeconds) {
        super(false);
        this.deltaSeconds = deltaSeconds;
    }

    public float getDeltaSeconds() {
        return deltaSeconds;
    }

    @Override
    public String toString() {
        return "ClientPrePlayerUpdateEvent{" +
            "deltaSeconds=" + deltaSeconds +
            '}';
    }
}
