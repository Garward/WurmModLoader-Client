package com.garward.wurmmodloader.client.api.events.eventlogic;

/**
 * Event logic for client tick and timing calculations.
 *
 * <p>This class contains constants and utility methods for client-side timing,
 * following the WurmModLoader architecture rule: <strong>NO LOGIC IN PATCHES</strong>.</p>
 *
 * <h2>Architecture Pattern:</h2>
 * <p>Patches should reference constants from this class instead of hardcoding values.</p>
 *
 * @since 0.2.0
 */
public final class ClientTickEventLogic {

    /**
     * Standard game tick rate in seconds.
     *
     * <p>Wurm Unlimited runs at 20 ticks per second, so each tick is 0.05 seconds (50ms).
     * This constant should be used by patches instead of hardcoding {@code 1.0f / 20.0f}.</p>
     */
    public static final float STANDARD_TICK_DELTA = 1.0f / 20.0f; // 0.05 seconds (50ms)

    /**
     * Standard game tick rate in ticks per second.
     */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * Standard game tick rate in milliseconds.
     */
    public static final int TICK_DURATION_MS = 50;

    private ClientTickEventLogic() {
        // Utility class
    }

    /**
     * Convert ticks to seconds.
     *
     * @param ticks Number of ticks
     * @return Time in seconds
     */
    public static float ticksToSeconds(int ticks) {
        return ticks * STANDARD_TICK_DELTA;
    }

    /**
     * Convert seconds to ticks (rounded).
     *
     * @param seconds Time in seconds
     * @return Number of ticks
     */
    public static int secondsToTicks(float seconds) {
        return Math.round(seconds * TICKS_PER_SECOND);
    }

    /**
     * Convert milliseconds to ticks (rounded).
     *
     * @param milliseconds Time in milliseconds
     * @return Number of ticks
     */
    public static int millisecondsToTicks(long milliseconds) {
        return (int) (milliseconds / TICK_DURATION_MS);
    }
}
