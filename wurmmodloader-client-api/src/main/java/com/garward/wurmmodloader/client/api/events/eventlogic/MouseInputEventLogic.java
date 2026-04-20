package com.garward.wurmmodloader.client.api.events.eventlogic;

import java.util.HashMap;
import java.util.Map;

/**
 * Event logic helper for mouse input processing.
 *
 * <p>This class handles complex logic that should NOT be in bytecode patches:
 * <ul>
 *   <li>Determining button type from method name</li>
 *   <li>Tracking mouse positions for delta calculation</li>
 *   <li>State management for drag operations</li>
 * </ul>
 *
 * <p>Pattern: Patches stay simple (just pass raw data), this class does the processing.
 *
 * @since 0.2.0
 */
public class MouseInputEventLogic {

    // Track last mouse position per component for delta calculation
    private static final Map<Object, MousePosition> lastPositions = new HashMap<>();

    /**
     * Determine button index from method name.
     *
     * @param methodName method name from bytecode (leftPressed, rightPressed, etc.)
     * @return button index (0=left, 1=right, 2=middle)
     */
    public static int getButtonFromMethodName(String methodName) {
        if (methodName.startsWith("left")) {
            return 0; // Left button
        } else if (methodName.startsWith("right")) {
            return 1; // Right button
        } else if (methodName.startsWith("middle")) {
            return 2; // Middle button
        }
        return 0; // Default to left
    }

    /**
     * Determine pressed state from method name.
     *
     * @param methodName method name from bytecode (leftPressed, leftReleased, etc.)
     * @return true if pressed, false if released
     */
    public static boolean getPressedStateFromMethodName(String methodName) {
        return methodName.endsWith("Pressed") || methodName.endsWith("pressed");
    }

    /**
     * Calculate mouse delta and update tracking.
     *
     * @param component the component being dragged on
     * @param currentX current mouse X position
     * @param currentY current mouse Y position
     * @return array [deltaX, deltaY]
     */
    public static int[] calculateMouseDelta(Object component, int currentX, int currentY) {
        MousePosition last = lastPositions.get(component);

        int deltaX = 0;
        int deltaY = 0;

        if (last != null) {
            deltaX = currentX - last.x;
            deltaY = currentY - last.y;
        }

        // Update tracking
        lastPositions.put(component, new MousePosition(currentX, currentY));

        return new int[] { deltaX, deltaY };
    }

    /**
     * Clear tracking for a component (call when component is removed).
     *
     * @param component the component to clear
     */
    public static void clearTracking(Object component) {
        lastPositions.remove(component);
    }

    /**
     * Reset tracking for all components (call on HUD reset).
     */
    public static void resetAllTracking() {
        lastPositions.clear();
    }

    /**
     * Simple position holder.
     */
    private static class MousePosition {
        final int x, y;

        MousePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
