package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the player's Field of View (FOV) setting changes.
 *
 * <p>This event fires whenever the horizontal FOV is modified, whether through:
 * <ul>
 *   <li>In-game settings UI</li>
 *   <li>Console commands</li>
 *   <li>Loading saved settings</li>
 *   <li>Any programmatic FOV change</li>
 * </ul>
 *
 * <h2>Use Cases:</h2>
 * <ul>
 *   <li>Adjusting HUD elements based on FOV</li>
 *   <li>Recalculating camera-dependent UI positioning</li>
 *   <li>Logging FOV changes for debugging</li>
 *   <li>Syncing custom camera systems</li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * @SubscribeEvent
 * public void onFOVChanged(FOVChangedEvent event) {
 *     logger.info("FOV changed from " + event.getOldFOV() + " to " + event.getNewFOV());
 *     updateCameraProjection(event.getNewFOV());
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class FOVChangedEvent extends Event {

    private final int oldFOV;
    private final int newFOV;

    /**
     * Creates a new FOV changed event.
     *
     * @param oldFOV the previous FOV value (degrees)
     * @param newFOV the new FOV value (degrees)
     */
    public FOVChangedEvent(int oldFOV, int newFOV) {
        this.oldFOV = oldFOV;
        this.newFOV = newFOV;
    }

    /**
     * Gets the previous FOV value.
     *
     * @return the old FOV in degrees (typically 60-110)
     */
    public int getOldFOV() {
        return oldFOV;
    }

    /**
     * Gets the new FOV value.
     *
     * @return the new FOV in degrees (typically 60-110)
     */
    public int getNewFOV() {
        return newFOV;
    }

    /**
     * Gets the change in FOV (positive if increased, negative if decreased).
     *
     * @return the FOV delta
     */
    public int getFOVDelta() {
        return newFOV - oldFOV;
    }

    @Override
    public String toString() {
        return "FOVChangedEvent{oldFOV=" + oldFOV + ", newFOV=" + newFOV + ", delta=" + getFOVDelta() + "}";
    }
}
