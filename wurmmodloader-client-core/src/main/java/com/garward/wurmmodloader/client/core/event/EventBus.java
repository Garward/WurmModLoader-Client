package com.garward.wurmmodloader.client.core.event;

import com.garward.wurmmodloader.client.api.events.base.Event;
import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.base.EventPriority;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central event bus for client mod communication and lifecycle management.
 *
 * <p>The EventBus provides a publish-subscribe mechanism for events.
 * Mods can subscribe to events using {@link SubscribeEvent} annotations
 * and post events to notify other mods.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * // Register a listener
 * EventBus bus = EventBus.getInstance();
 * bus.register(myMod);
 *
 * // Post an event
 * ClientInitEvent event = new ClientInitEvent();
 * bus.post(event);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>The EventBus is thread-safe for registration and posting. However,
 * events are dispatched synchronously on the calling thread.
 *
 * <h2>Error Handling</h2>
 * <p>If an event handler throws an exception, it is logged and other
 * handlers continue to be called. Exceptions do not propagate to the poster.
 *
 * @since 0.1.0
 * @see SubscribeEvent
 * @see Event
 */
public class EventBus {

    private static final Logger logger = Logger.getLogger(EventBus.class.getName());

    private static final EventBus INSTANCE = new EventBus();

    /**
     * Map from event type to list of subscribers.
     * Uses ConcurrentHashMap for thread-safe access.
     * Uses CopyOnWriteArrayList for thread-safe iteration during event dispatch.
     */
    private final Map<Class<? extends Event>, List<EventSubscriber>> subscribers;

    /**
     * Set of registered listener objects to prevent duplicate registration.
     */
    private final Set<Object> registeredListeners;

    /**
     * Creates a new EventBus.
     */
    public EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
        this.registeredListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    /**
     * Returns the singleton EventBus instance.
     *
     * @return the event bus
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Registers all {@link SubscribeEvent} annotated methods in the given listener.
     *
     * <p>Scans the listener object for methods annotated with {@code @SubscribeEvent}
     * and registers them as event handlers. Methods must:
     * <ul>
     *   <li>Have exactly one parameter</li>
     *   <li>The parameter type must extend {@link Event}</li>
     *   <li>Return void</li>
     * </ul>
     *
     * @param listener the listener object to register
     * @throws IllegalArgumentException if the listener has already been registered
     *                                  or if subscriber methods are invalid
     */
    public void register(Object listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Cannot register null listener");
        }

        if (!registeredListeners.add(listener)) {
            logger.log(Level.WARNING, "Listener already registered: " + listener.getClass().getName());
            return;
        }

        Class<?> listenerClass = listener.getClass();
        int subscriberCount = 0;

        // Scan all methods in the class hierarchy
        for (Method method : getAllMethods(listenerClass)) {
            SubscribeEvent annotation = getSubscribeAnnotation(method);
            if (annotation == null) {
                continue;
            }

            // Validate method signature
            if (!isValidSubscriberMethod(method)) {
                throw new IllegalArgumentException(
                    "Invalid @SubscribeEvent method: " + method +
                    " - must have exactly one parameter extending Event and return void");
            }

            // Get event type from parameter
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) method.getParameterTypes()[0];

            // Create subscriber
            EventSubscriber subscriber = new EventSubscriber(
                listener,
                method,
                eventType,
                annotation.priority(),
                annotation.receiveCancelled()
            );

            // Add to subscribers list for this event type
            List<EventSubscriber> eventSubscribers = subscribers.computeIfAbsent(
                eventType,
                k -> new CopyOnWriteArrayList<>()
            );
            eventSubscribers.add(subscriber);

            // Sort by priority (higher priority first)
            Collections.sort(eventSubscribers);

            subscriberCount++;
            logger.log(Level.FINE, "Registered event handler: " + subscriber);
        }

        logger.log(Level.INFO, "Registered " + subscriberCount +
            " event handler(s) from " + listenerClass.getName());
    }

    /**
     * Unregisters all event handlers from the given listener.
     *
     * @param listener the listener to unregister
     */
    public void unregister(Object listener) {
        if (listener == null || !registeredListeners.remove(listener)) {
            return;
        }

        int removedCount = 0;
        for (List<EventSubscriber> eventSubscribers : subscribers.values()) {
            int sizeBefore = eventSubscribers.size();
            eventSubscribers.removeIf(sub -> sub.getListener() == listener);
            removedCount += (sizeBefore - eventSubscribers.size());
        }

        logger.log(Level.INFO, "Unregistered " + removedCount +
            " event handler(s) from " + listener.getClass().getName());
    }

    /**
     * Posts an event to all registered subscribers.
     *
     * <p>Subscribers are called in priority order (highest first). If the event
     * is cancellable and a subscriber cancels it, subsequent subscribers with
     * {@code receiveCancelled=false} will not be called.
     *
     * <p>Events are dispatched synchronously on the calling thread.
     *
     * @param event the event to post
     * @param <T> the event type
     * @return the event (potentially modified by handlers)
     */
    public <T extends Event> T post(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Cannot post null event");
        }

        Class<? extends Event> eventType = event.getClass();
        List<EventSubscriber> eventSubscribers = subscribers.get(eventType);

        if (eventSubscribers == null || eventSubscribers.isEmpty()) {
            logger.log(Level.FINEST, "No subscribers for event: " + eventType.getSimpleName());
            return event;
        }

        logger.log(Level.FINEST, "Posting event: " + event +
            " to " + eventSubscribers.size() + " subscriber(s)");

        for (EventSubscriber subscriber : eventSubscribers) {
            subscriber.invoke(event);

            // If event was cancelled and subscriber doesn't receive cancelled events,
            // stop dispatching (handled in EventSubscriber.invoke)
        }

        return event;
    }

    /**
     * Returns the number of registered listeners.
     *
     * @return the listener count
     */
    public int getListenerCount() {
        return registeredListeners.size();
    }

    /**
     * Returns the number of subscribers for a given event type.
     *
     * @param eventType the event type
     * @return the subscriber count
     */
    public int getSubscriberCount(Class<? extends Event> eventType) {
        List<EventSubscriber> eventSubscribers = subscribers.get(eventType);
        return eventSubscribers != null ? eventSubscribers.size() : 0;
    }

    /**
     * Returns all registered event types.
     *
     * @return set of event types
     */
    public Set<Class<? extends Event>> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(subscribers.keySet());
    }

    /**
     * Clears all registered listeners.
     * Use with caution - typically only needed for testing.
     */
    public void unregisterAll() {
        subscribers.clear();
        registeredListeners.clear();
        logger.log(Level.INFO, "Unregistered all event handlers");
    }

    /**
     * Validates that a method is a valid event subscriber.
     */
    private boolean isValidSubscriberMethod(Method method) {
        // Must have exactly one parameter
        if (method.getParameterCount() != 1) {
            return false;
        }

        // Parameter must extend Event
        Class<?> paramType = method.getParameterTypes()[0];
        if (!Event.class.isAssignableFrom(paramType)) {
            return false;
        }

        // Must return void
        if (method.getReturnType() != void.class) {
            return false;
        }

        return true;
    }

    /**
     * Gets all methods from the class hierarchy, including inherited methods.
     */
    private List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    private SubscribeEvent getSubscribeAnnotation(Method method) {
        SubscribeEvent direct = method.getAnnotation(SubscribeEvent.class);
        if (direct != null) {
            return direct;
        }

        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            if (!annotation.annotationType().getName().equals(SubscribeEvent.class.getName())) {
                continue;
            }
            return createBridgedAnnotation(annotation);
        }
        return null;
    }

    private SubscribeEvent createBridgedAnnotation(java.lang.annotation.Annotation foreignAnnotation) {
        return new SubscribeEvent() {
            @Override
            public EventPriority priority() {
                try {
                    Object value = foreignAnnotation.annotationType()
                        .getMethod("priority")
                        .invoke(foreignAnnotation);
                    return value instanceof EventPriority
                        ? (EventPriority) value
                        : EventPriority.valueOf(value.toString());
                } catch (ReflectiveOperationException e) {
                    logger.log(Level.WARNING,
                        "Failed to read priority() from foreign SubscribeEvent annotation",
                        e);
                    return EventPriority.NORMAL;
                }
            }

            @Override
            public boolean receiveCancelled() {
                try {
                    Object value = foreignAnnotation.annotationType()
                        .getMethod("receiveCancelled")
                        .invoke(foreignAnnotation);
                    return (Boolean) value;
                } catch (ReflectiveOperationException e) {
                    logger.log(Level.WARNING,
                        "Failed to read receiveCancelled() from foreign SubscribeEvent annotation",
                        e);
                    return false;
                }
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SubscribeEvent.class;
            }
        };
    }
}
