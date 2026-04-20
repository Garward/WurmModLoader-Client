package com.garward.wurmmodloader.client.core.event;

import com.garward.wurmmodloader.client.api.events.base.Event;
import com.garward.wurmmodloader.client.api.events.base.EventPriority;
import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds information about an event subscriber method.
 *
 * <p>This class wraps a method annotated with {@link SubscribeEvent} and provides
 * functionality to invoke it when an event is posted.
 *
 * @since 0.1.0
 */
public class EventSubscriber implements Comparable<EventSubscriber> {

    private static final Logger logger = Logger.getLogger(EventSubscriber.class.getName());

    private final Object listener;
    private final Method method;
    private final Class<? extends Event> eventType;
    private final EventPriority priority;
    private final boolean receiveCancelled;

    /**
     * Creates a new EventSubscriber.
     *
     * @param listener the object containing the subscriber method
     * @param method the subscriber method
     * @param eventType the event type this subscriber handles
     * @param priority the priority of this subscriber
     * @param receiveCancelled whether to receive cancelled events
     */
    public EventSubscriber(Object listener, Method method, Class<? extends Event> eventType,
                          EventPriority priority, boolean receiveCancelled) {
        this.listener = listener;
        this.method = method;
        this.eventType = eventType;
        this.priority = priority;
        this.receiveCancelled = receiveCancelled;

        // Ensure method is accessible
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
    }

    /**
     * Invokes this subscriber with the given event.
     *
     * @param event the event to pass to the subscriber
     * @return {@code true} if invocation succeeded, {@code false} if an error occurred
     */
    public boolean invoke(Event event) {
        // Skip if event is cancelled and we don't receive cancelled events
        if (event.isCancellable() && event.isCancelled() && !receiveCancelled) {
            return true; // Not an error, just skipped
        }

        try {
            method.invoke(listener, event);
            return true;
        } catch (IllegalAccessException e) {
            logger.log(Level.SEVERE, "Failed to invoke event handler: " + method, e);
            return false;
        } catch (InvocationTargetException e) {
            // Log the actual exception that occurred in the handler
            Throwable cause = e.getCause();
            logger.log(Level.SEVERE,
                "Event handler threw exception: " + method + " in " + listener.getClass().getName(),
                cause != null ? cause : e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error invoking event handler: " + method, e);
            return false;
        }
    }

    /**
     * Returns the event type this subscriber handles.
     *
     * @return the event type
     */
    public Class<? extends Event> getEventType() {
        return eventType;
    }

    /**
     * Returns the priority of this subscriber.
     *
     * @return the priority
     */
    public EventPriority getPriority() {
        return priority;
    }

    /**
     * Returns the listener object containing the subscriber method.
     *
     * @return the listener
     */
    public Object getListener() {
        return listener;
    }

    /**
     * Returns the subscriber method.
     *
     * @return the method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Compares subscribers by priority.
     * Higher priority (lower numeric value) comes first.
     */
    @Override
    public int compareTo(EventSubscriber other) {
        return this.priority.compareByValue(other.priority);
    }

    @Override
    public String toString() {
        return String.format("EventSubscriber[%s.%s, priority=%s, event=%s]",
            listener.getClass().getSimpleName(),
            method.getName(),
            priority,
            eventType.getSimpleName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EventSubscriber)) return false;
        EventSubscriber other = (EventSubscriber) obj;
        return listener == other.listener && method.equals(other.method);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(listener) + method.hashCode();
    }
}
