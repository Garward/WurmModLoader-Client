package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the Wurm client finishes initialization.
 *
 * <p>This event is fired after the client has initialized but before
 * the world is loaded. It's a good place to set up client-side state,
 * register hooks, or prepare resources.
 *
 * @since 0.1.0
 */
public class ClientInitEvent extends Event {

    /**
     * Creates a new ClientInitEvent.
     */
    public ClientInitEvent() {
        super(false);  // Not cancellable
    }

    @Override
    public String toString() {
        return "ClientInitEvent{}";
    }
}
