package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fires whenever the client receives a stamina update from the server
 * (CMD_STAMINA). Both values are in the 0.0–1.0 range vanilla uses internally.
 *
 * <p>The damage parameter the server also sends with this packet is ignored
 * — separate event if a future mod needs it.
 *
 * @since 0.4.0
 */
public class ClientStaminaChangedEvent extends Event {

    private final float oldStamina;
    private final float newStamina;

    public ClientStaminaChangedEvent(float oldStamina, float newStamina) {
        this.oldStamina = oldStamina;
        this.newStamina = newStamina;
    }

    /** Stamina before the update, 0.0–1.0. {@code Float.NaN} on the first event of a session. */
    public float getOldStamina() { return oldStamina; }

    /** Stamina after the update, 0.0–1.0. */
    public float getNewStamina() { return newStamina; }

    /** Convenience: did this update bring stamina to >= the threshold (e.g. "full")? */
    public boolean isAtLeast(float threshold) { return newStamina >= threshold; }

    @Override
    public String toString() {
        return "ClientStaminaChangedEvent{old=" + oldStamina + ", new=" + newStamina + "}";
    }
}
