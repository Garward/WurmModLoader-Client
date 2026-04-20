package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when mouse is dragged on a custom GUI component.
 *
 * @since 0.2.0
 */
public class MouseDragEvent extends Event {

    private final Object component;
    private final int mouseX, mouseY;
    private final int deltaX, deltaY;

    public MouseDragEvent(Object component, int mouseX, int mouseY, int deltaX, int deltaY) {
        super(true); // Cancellable

        this.component = component;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public Object getComponent() {
        return component;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public int getDeltaX() {
        return deltaX;
    }

    public int getDeltaY() {
        return deltaY;
    }

    @Override
    public String toString() {
        return "MouseDrag[x=" + mouseX + ", y=" + mouseY + ", dx=" + deltaX + ", dy=" + deltaY + "]";
    }
}
