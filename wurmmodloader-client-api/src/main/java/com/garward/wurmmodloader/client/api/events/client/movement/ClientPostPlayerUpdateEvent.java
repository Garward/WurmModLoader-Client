package com.garward.wurmmodloader.client.api.events.client.movement;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired once per client tick immediately after the local player's transform
 * has been updated by the vanilla client.
 * <p>
 * Useful for post-processing, debug overlays and sanity checks.
 */
public final class ClientPostPlayerUpdateEvent extends Event {

    private final float deltaSeconds;

    public ClientPostPlayerUpdateEvent(float deltaSeconds) {
        super(false);
        this.deltaSeconds = deltaSeconds;
    }

    public float getDeltaSeconds() {
        return deltaSeconds;
    }

    @Override
    public String toString() {
        return "ClientPostPlayerUpdateEvent{" +
            "deltaSeconds=" + deltaSeconds +
            '}';
    }
}
