package com.garward.wurmmodloader.client.api.events.render;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the end of {@code WorldRender.renderPickedItem(Queue)} — the visible
 * pass that draws the currently-picked geometry. Overlay mods (ESP, markers,
 * waypoint lines) queue their primitives here so they appear on-screen rather
 * than only in the pick buffer.
 *
 * @since 0.3.0
 */
public class WorldRenderPostEvent extends Event {

    private final Object queue;
    private final Object worldRender;
    private final Object pickRenderer;

    public WorldRenderPostEvent(Object queue, Object worldRender, Object pickRenderer) {
        super(false);
        this.queue = queue;
        this.worldRender = worldRender;
        this.pickRenderer = pickRenderer;
    }

    /** Active render queue ({@code com.wurmonline.client.renderer.backend.Queue}). */
    public Object getQueue() {
        return queue;
    }

    /** The WorldRender instance firing this event. */
    public Object getWorldRender() {
        return worldRender;
    }

    /**
     * The {@code PickRenderer} used during this pass. Needed by overlays that
     * build their own render-state objects via {@code PickRenderer}'s inner
     * classes (e.g. ESP's {@code CustomPickFillRender}).
     */
    public Object getPickRenderer() {
        return pickRenderer;
    }

    @Override
    public String toString() {
        return "WorldRenderPost[queue=" + queue + "]";
    }
}
