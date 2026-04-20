package com.garward.wurmmodloader.client.api.events.base;

/**
 * Base class for all client events in WurmModLoader Client.
 *
 * <p>Events represent significant moments in the client lifecycle or gameplay
 * that mods can observe and potentially modify. Events are dispatched through
 * the {@code EventBus} to all registered listeners.
 *
 * <h2>Event Cancellation</h2>
 * <p>Some events are cancellable, meaning that a listener can prevent the
 * associated action from occurring. To make an event cancellable, pass
 * {@code true} to the constructor.
 *
 * @since 0.1.0
 * @see SubscribeEvent
 */
public abstract class Event {

    private final boolean cancellable;
    private boolean cancelled = false;

    /**
     * Creates a new non-cancellable event.
     */
    protected Event() {
        this(false);
    }

    /**
     * Creates a new event.
     *
     * @param cancellable whether this event can be cancelled
     */
    protected Event(boolean cancellable) {
        this.cancellable = cancellable;
    }

    /**
     * Returns whether this event can be cancelled.
     *
     * @return {@code true} if this event supports cancellation
     */
    public boolean isCancellable() {
        return cancellable;
    }

    /**
     * Sets the cancellation state of this event.
     *
     * @param cancel {@code true} to cancel this event
     * @throws UnsupportedOperationException if this event is not cancellable
     */
    public void setCancelled(boolean cancel) {
        if (!cancellable) {
            throw new UnsupportedOperationException(
                "Cannot cancel non-cancellable event: " + getClass().getName());
        }
        this.cancelled = cancel;
    }

    /**
     * Returns whether this event has been cancelled.
     *
     * @return {@code true} if this event has been cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Convenience method to cancel this event.
     */
    public void cancel() {
        setCancelled(true);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               (cancellable ? (cancelled ? " [CANCELLED]" : " [cancellable]") : "");
    }
}
