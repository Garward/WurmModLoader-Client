package com.garward.wurmmodloader.client.api.events.render;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when a {@code CellRenderable} is removed from the world — the engine
 * has dispatched its {@code removed(boolean)} callback. Subscribers can evict
 * tracking state keyed on the renderable.
 *
 * @since 0.3.0
 */
public class CellRenderableRemovedEvent extends Event {

    private final Object renderable;
    private final boolean removeEffects;

    public CellRenderableRemovedEvent(Object renderable, boolean removeEffects) {
        super(false);
        this.renderable = renderable;
        this.removeEffects = removeEffects;
    }

    public Object getRenderable() {
        return renderable;
    }

    public boolean isRemoveEffects() {
        return removeEffects;
    }

    @Override
    public String toString() {
        return "CellRenderableRemoved[" + renderable + ",fx=" + removeEffects + "]";
    }
}
