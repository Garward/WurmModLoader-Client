package com.garward.wurmmodloader.client.api.events.base;

/**
 * Event listener priority levels.
 *
 * <p>Priority determines the order in which event listeners are called.
 * Listeners with higher priority (lower numeric value) are called first.
 * Within the same priority level, listeners are called in registration order.
 *
 * <h2>Priority Levels</h2>
 * <ul>
 *   <li><b>HIGHEST</b> - Critical listeners that must run first (security, validation)</li>
 *   <li><b>HIGH</b> - Important listeners that should run early</li>
 *   <li><b>NORMAL</b> - Default priority for most listeners</li>
 *   <li><b>LOW</b> - Listeners that should run late (logging, cleanup)</li>
 *   <li><b>LOWEST</b> - Final listeners that run last (final logging, telemetry)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @SubscribeEvent(priority = EventPriority.HIGH)
 * public void onClientInit(ClientInitEvent event) {
 *     // This runs before NORMAL priority listeners
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @see SubscribeEvent
 */
public enum EventPriority {
    /**
     * Highest priority - runs first.
     * Use for critical operations like security checks and validation.
     */
    HIGHEST(0),

    /**
     * High priority - runs early.
     * Use for important operations that need to run before most handlers.
     */
    HIGH(1),

    /**
     * Normal priority - default.
     * Use for standard event handling.
     */
    NORMAL(2),

    /**
     * Low priority - runs late.
     * Use for operations that should run after most handlers.
     */
    LOW(3),

    /**
     * Lowest priority - runs last.
     * Use for cleanup, logging, and telemetry.
     */
    LOWEST(4);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of this priority.
     * Lower values indicate higher priority.
     *
     * @return the priority value
     */
    public int getValue() {
        return value;
    }

    /**
     * Compares this priority with another based on numeric value.
     *
     * @param other the other priority to compare with
     * @return negative if this priority is higher, positive if lower, 0 if equal
     */
    public int compareByValue(EventPriority other) {
        return Integer.compare(this.value, other.value);
    }
}
