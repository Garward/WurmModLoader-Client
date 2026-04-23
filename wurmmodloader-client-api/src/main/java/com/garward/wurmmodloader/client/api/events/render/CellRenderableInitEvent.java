package com.garward.wurmmodloader.client.api.events.render;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when a {@code CellRenderable} finishes construction/initialization —
 * i.e. a creature, player, or ground item becomes present in the world.
 *
 * <p>Lets overlay/tracking mods replace reflective scans of
 * {@code world.cellRenderer.creatures} with a push-based lifecycle.
 *
 * @since 0.3.0
 */
public class CellRenderableInitEvent extends Event {

    private final Object renderable;

    public CellRenderableInitEvent(Object renderable) {
        super(false);
        this.renderable = renderable;
    }

    /** The new renderable ({@code com.wurmonline.client.renderer.cell.CellRenderable}). */
    public Object getRenderable() {
        return renderable;
    }

    @Override
    public String toString() {
        return "CellRenderableInit[" + renderable + "]";
    }
}
