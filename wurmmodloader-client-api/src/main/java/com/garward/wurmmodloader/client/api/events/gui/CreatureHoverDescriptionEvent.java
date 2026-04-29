package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the end of
 * {@code com.wurmonline.client.renderer.cell.CreatureCellRenderable.getHoverDescription(PickData)}
 * — vanilla's hover-description lines (kingdom, hover text, dev text)
 * have already been added to the {@code PickData} accumulator. Subscribers
 * append additional lines via {@link #getPickData()}.{@code addText(...)}.
 *
 * <p>The patch pre-extracts the creature's model name (e.g.
 * {@code "horse.hell.cinder.male"}) so subscribers can derive coat / sex /
 * variant labels without reflecting on the private {@code creature} field.
 *
 * @since 0.4.1
 */
public class CreatureHoverDescriptionEvent extends Event {

    private final Object renderable;
    private final Object pickData;
    private final String modelName;

    public CreatureHoverDescriptionEvent(Object renderable, Object pickData, String modelName) {
        super(false);
        this.renderable = renderable;
        this.pickData = pickData;
        this.modelName = modelName;
    }

    /** The {@code com.wurmonline.client.renderer.cell.CreatureCellRenderable} instance. */
    public Object getRenderable() {
        return renderable;
    }

    /**
     * The {@code com.wurmonline.client.renderer.PickData} accumulator —
     * subscribers call {@code addText(String)} on this to append lines.
     */
    public Object getPickData() {
        return pickData;
    }

    /**
     * Lower-cased model name (e.g. {@code "horse.hell.cinder.male"}).
     * Pre-extracted from {@code creature.getModelName()} because the
     * {@code creature} field is private on {@code CreatureCellRenderable}.
     */
    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return "CreatureHoverDescription[" + modelName + "]";
    }
}
