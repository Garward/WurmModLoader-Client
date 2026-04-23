package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired after {@code CompassComponent.pick(PickData, int, int)}. Subscribers
 * can append additional hover text (angle, position, height, etc.) via the
 * passed {@code PickData}.
 *
 * @since 0.3.0
 */
public class CompassComponentPickEvent extends Event {

    private final Object component;
    private final Object pickData;
    private final int mouseX;
    private final int mouseY;

    public CompassComponentPickEvent(Object component, Object pickData, int mouseX, int mouseY) {
        super(false);
        this.component = component;
        this.pickData = pickData;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** The {@code com.wurmonline.client.renderer.gui.CompassComponent} instance. */
    public Object getComponent() {
        return component;
    }

    /** The {@code com.wurmonline.client.renderer.PickData} accumulator. */
    public Object getPickData() {
        return pickData;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    @Override
    public String toString() {
        return "CompassComponentPick[" + component + "," + mouseX + "," + mouseY + "]";
    }
}
