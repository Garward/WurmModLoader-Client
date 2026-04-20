package com.garward.wurmmodloader.client.api.events.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler for the client event bus.
 *
 * <p>Methods annotated with {@code @SubscribeEvent} will be automatically
 * registered to receive events when their containing object is registered
 * with the event bus.
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Method must be public</li>
 *   <li>Method must have exactly one parameter (the event type)</li>
 *   <li>Method return type is ignored (can be void)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * public class MyMod {
 *
 *     @SubscribeEvent
 *     public void onClientInit(ClientInitEvent event) {
 *         System.out.println("Client initialized!");
 *     }
 *
 *     @SubscribeEvent(priority = EventPriority.HIGH)
 *     public void onClientTick(ClientTickEvent event) {
 *         // High priority tick handler
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    /**
     * The priority of this event handler.
     * Higher priority (lower numeric value) handlers are called first.
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Whether to receive cancelled events.
     *
     * <p>By default, handlers are not called for events that have been cancelled
     * by a higher-priority handler. Set this to {@code true} to receive cancelled
     * events anyway.
     *
     * @return {@code true} to receive cancelled events
     */
    boolean receiveCancelled() default false;
}
