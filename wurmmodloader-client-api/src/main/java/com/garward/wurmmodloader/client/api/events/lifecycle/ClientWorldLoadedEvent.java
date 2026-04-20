package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the Wurm client finishes loading the world.
 *
 * <p>This event is fired after the world terrain and entities have been
 * loaded and are ready for interaction. Use this event to perform any
 * initialization that requires the world to be present.
 *
 * @since 0.1.0
 */
public class ClientWorldLoadedEvent extends Event {

    /**
     * Creates a new ClientWorldLoadedEvent.
     */
    public ClientWorldLoadedEvent() {
        super(false);  // Not cancellable
    }

    @Override
    public String toString() {
        return "ClientWorldLoadedEvent{}";
    }
}
