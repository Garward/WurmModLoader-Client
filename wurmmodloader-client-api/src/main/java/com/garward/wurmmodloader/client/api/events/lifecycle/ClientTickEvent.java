package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired every client tick (frame).
 *
 * <p>This event is fired during the main client game loop, once per frame.
 * Use this for operations that need to run continuously, such as:
 * <ul>
 *   <li>Client-side prediction updates</li>
 *   <li>Animation interpolation</li>
 *   <li>Input processing</li>
 *   <li>HUD updates</li>
 * </ul>
 *
 * <p><b>Warning:</b> This event fires very frequently (typically 60+ times
 * per second). Keep handlers lightweight to avoid performance issues.
 *
 * @since 0.1.0
 */
public class ClientTickEvent extends Event {

    private final float deltaTime;

    /**
     * Creates a new ClientTickEvent.
     *
     * @param deltaTime time elapsed since the last tick in seconds
     */
    public ClientTickEvent(float deltaTime) {
        super(false);  // Not cancellable
        this.deltaTime = deltaTime;
    }

    /**
     * Returns the time elapsed since the last tick.
     *
     * @return delta time in seconds
     */
    public float getDeltaTime() {
        return deltaTime;
    }

    @Override
    public String toString() {
        return String.format("ClientTickEvent{deltaTime=%.3f}", deltaTime);
    }
}
