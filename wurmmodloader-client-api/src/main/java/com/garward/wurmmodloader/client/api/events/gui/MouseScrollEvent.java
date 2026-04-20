package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when mouse wheel is scrolled on a custom GUI component.
 *
 * @since 0.2.0
 */
public class MouseScrollEvent extends Event {

    private final Object component;
    private final int delta; // Positive = scroll up, negative = scroll down

    public MouseScrollEvent(Object component, int delta) {
        super(true); // Cancellable

        this.component = component;
        this.delta = delta;
    }

    public Object getComponent() {
        return component;
    }

    public int getDelta() {
        return delta;
    }

    public boolean isScrollUp() {
        return delta > 0;
    }

    public boolean isScrollDown() {
        return delta < 0;
    }

    @Override
    public String toString() {
        return "MouseScroll[delta=" + delta + "]";
    }
}
