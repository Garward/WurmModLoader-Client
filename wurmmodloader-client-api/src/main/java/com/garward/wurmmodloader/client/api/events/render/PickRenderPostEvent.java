package com.garward.wurmmodloader.client.api.events.render;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the end of {@code PickRenderer.execute(Queue)} — after the engine
 * has queued its own cell/terrain/cave/sky pick geometry but before the method
 * returns. Appropriate for overlay mods that want to append pickable or visual
 * primitives after the engine's native work.
 *
 * <p>The {@code queue} is the same {@code com.wurmonline.client.renderer.backend.Queue}
 * instance handed to the matching {@link PickRenderPreEvent}.
 *
 * @since 0.3.0
 */
public class PickRenderPostEvent extends Event {

    private final Object queue;

    public PickRenderPostEvent(Object queue) {
        super(false);
        this.queue = queue;
    }

    /** Active render queue ({@code com.wurmonline.client.renderer.backend.Queue}). */
    public Object getQueue() {
        return queue;
    }

    @Override
    public String toString() {
        return "PickRenderPost[queue=" + queue + "]";
    }
}
