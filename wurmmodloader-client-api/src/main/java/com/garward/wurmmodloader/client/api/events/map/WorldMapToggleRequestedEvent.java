package com.garward.wurmmodloader.client.api.events.map;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the vanilla Wurm client's world map toggle (M key or button) is invoked.
 *
 * <p>This event is fired at the very start of
 * {@code HeadsUpDisplay.toggleWorldMapVisible()} by {@code WorldMapTogglePatch}.
 * Mods that want to replace the hardcoded vanilla "Freedom Isles" parchment with
 * their own window subscribe to this event, open/close their custom window, and
 * then call {@link #suppressVanilla()} to prevent the vanilla code path from
 * running.
 *
 * <p>Multiple mods can cooperate: the first handler to call
 * {@link #suppressVanilla()} wins — downstream handlers still receive the event
 * and can observe the suppression via {@link #isSuppressed()} if they need to.
 *
 * <p>If no handler suppresses, the vanilla parchment opens as normal (useful
 * fallback for when the custom window fails to initialize).
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onWorldMapToggle(WorldMapToggleRequestedEvent event) {
 *     customWindow.toggle();
 *     event.suppressVanilla();
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class WorldMapToggleRequestedEvent extends Event {

    private final Object hud; // HeadsUpDisplay (opaque here, mods cast)
    private boolean suppressed = false;

    /**
     * Creates a new WorldMapToggleRequestedEvent.
     *
     * @param hud the HeadsUpDisplay instance that was toggled
     */
    public WorldMapToggleRequestedEvent(Object hud) {
        super(false); // Not cancellable in the Event-base sense; we use our own flag
        this.hud = hud;
    }

    /**
     * Returns the HeadsUpDisplay instance that invoked the toggle. Type is
     * {@code Object} so this event stays free of {@code com.wurmonline.*}
     * imports. Cast in your handler if you need HUD-specific APIs.
     */
    public Object getHud() {
        return hud;
    }

    /**
     * Marks the vanilla world map toggle as suppressed. After this is called,
     * {@code HeadsUpDisplay.toggleWorldMapVisible()} will short-circuit and
     * not open the hardcoded parchment.
     *
     * <p>Call this only after successfully opening/toggling your replacement
     * window. If your window fails to initialize, leave the flag unset so the
     * user still sees something.
     */
    public void suppressVanilla() {
        this.suppressed = true;
    }

    /**
     * Returns true if any handler has called {@link #suppressVanilla()}.
     * Used by the bytecode patch to decide whether to skip vanilla code.
     */
    public boolean isSuppressed() {
        return suppressed;
    }

    @Override
    public String toString() {
        return "WorldMapToggleRequested[suppressed=" + suppressed + "]";
    }
}
