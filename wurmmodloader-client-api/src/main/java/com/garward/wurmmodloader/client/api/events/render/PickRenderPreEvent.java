package com.garward.wurmmodloader.client.api.events.render;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the start of {@code PickRenderer.execute(Queue)} — before any pick
 * primitives have been queued for the frame. Mods can inject their own primitives
 * into the supplied queue to draw ESP-style overlays, highlights, or custom
 * pickable geometry in the same pass the engine uses for picking.
 *
 * <p>The {@code queue} is the active {@code com.wurmonline.client.renderer.backend.Queue}
 * for this frame. It is exposed as {@link Object} to keep the API jar free of
 * {@code com.wurmonline.*} imports; mods cast it locally.
 *
 * @since 0.3.0
 */
public class PickRenderPreEvent extends Event {

    private final Object queue;

    public PickRenderPreEvent(Object queue) {
        super(false);
        this.queue = queue;
    }

    /** Active render queue ({@code com.wurmonline.client.renderer.backend.Queue}). */
    public Object getQueue() {
        return queue;
    }

    @Override
    public String toString() {
        return "PickRenderPre[queue=" + queue + "]";
    }
}
